package it.polito.dp2.NFFG.sol2;

import it.polito.dp2.NFFG.lab2.NoGraphException;

import it.polito.dp2.NFFG.lab2.ReachabilityTester;
import it.polito.dp2.NFFG.lab2.ReachabilityTesterException;
import it.polito.dp2.NFFG.lab2.ServiceException;
import it.polito.dp2.NFFG.lab2.UnknownNameException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set; 
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import it.polito.dp2.NFFG.LinkReader;
import it.polito.dp2.NFFG.NffgReader;
import it.polito.dp2.NFFG.NffgVerifier;
import it.polito.dp2.NFFG.NodeReader;
/*
 * This class exploits the following functions:
 * a) read the information about a set of NFFGs from the random generator already used in Assignment 1; 
 * b) load one of these NFFGs into NEO4J by means of Neo4JXML; 
 * c) test reachability between pairs of nodes in the loaded NFFG.
 */
public class ConcreteReachabilityTester implements ReachabilityTester {

	private NffgVerifier monitor=null;
	private Set<NffgReader> setNffg=null;
	private Client client=null;
	private String graphName=null;
	
	private HashMap<String,Node> nodeNames;
	private String baseServiceUrl = null;

	public ConcreteReachabilityTester() throws ReachabilityTesterException{
			it.polito.dp2.NFFG.NffgVerifierFactory factory=null;
			try{
				baseServiceUrl = System.getProperty("it.polito.dp2.NFFG.lab2.URL");
				
				factory = it.polito.dp2.NFFG.NffgVerifierFactory.newInstance();
				monitor = factory.newNffgVerifier();
				setNffg = monitor.getNffgs();
				
				//create the connection with the Neo4J Database
				client = ClientBuilder.newClient();
				monitor = factory.newNffgVerifier();
			
			}catch(Exception e){
				System.out.println("Error in creating the service");
				e.printStackTrace();
				throw new ReachabilityTesterException();
			}catch(java.lang.Error e){
				System.out.println("Error in creating the service");
				e.printStackTrace();
				throw new ReachabilityTesterException();
			}
			
	}
	
	@Override
	public void loadNFFG(String name) throws UnknownNameException, ServiceException {
		
		NffgReader currentNffg;
			//read the list of nffg elements 
			List<NffgReader> elements = setNffg.stream().filter(n->{
				return n.getName().equals(name);
				
			}).collect(Collectors.toList());
			
			//no nffg found with the requested name
			if(elements.size()==0){
				graphName=null;
				throw new UnknownNameException("The NFFG " + name + "does not exist!!");
			}else{
				
				//DELETION of all nodes and relative links
				graphName=name;
				try{
					String resourceName = baseServiceUrl + "/resource/nodes"; 
					client.target(resourceName)
						.request()	
						.accept(MediaType.APPLICATION_XML).
						delete();
				}catch(WebApplicationException e){
					System.out.println("Error " + e.getResponse().getStatus() + " Received from server.\n" + e.getMessage());
					throw new ServiceException(e);
				}catch(Exception e){
					System.out.println("An Error occurred while deleting all the nodes");
					e.printStackTrace();
					throw new ServiceException(e);
				}
				currentNffg = elements.get(0);
				extractInformationOfNffgForDB(currentNffg);
			}
		
	}

	@Override
	public boolean testReachability(String srcName, String destName)
			throws UnknownNameException, ServiceException, NoGraphException {
		
			if(srcName==null || destName==null){
				System.out.println("Null passed as src or destination nodes ");
				throw new UnknownNameException("One of src/dst or both nodes are not present in the NFFG");
			}
			
			if(!nodeNames.containsKey(srcName) || !nodeNames.containsKey(destName)){
				System.out.println("Source or destination nodes are not existing");
				throw new UnknownNameException("One of src/dst or both nodes are not present in the NFFG");
			}

			if(graphName==null){
				System.out.println("No graph with the requested name has been loaded");
				throw new NoGraphException();
			}
			
			String srcID = nodeNames.get(srcName).getId();
			String dstID = nodeNames.get(destName).getId();
			
			//additional check to verify the node is existing in the graph
			try{
					String resourceName = baseServiceUrl + "/resource/node/" + srcID;
					client.target(resourceName)
						.request(MediaType.APPLICATION_XML)
						.accept(MediaType.APPLICATION_XML)
						.get(Node.class);
					
					resourceName = baseServiceUrl + "/resource/node/" + dstID;
					client.target(resourceName)
						.request(MediaType.APPLICATION_XML)
						.accept(MediaType.APPLICATION_XML)
						.get(Node.class);
			}catch(WebApplicationException e){
				System.out.println("Error " + e.getResponse().getStatus() + " Received from server.\n" + e.getMessage());
				System.out.println("Source or destination nodes are not existing");
				throw new UnknownNameException("One of src/dst or both nodes are not present in the NFFG");
			}catch(Exception e){
				System.out.println("Source or destination nodes are not existing");
				throw new UnknownNameException("One of src/dst or both nodes are not present in the NFFG");
			
			}
			
			
			if(srcID.equals(dstID)){
				return true;
			}

			try{
				String requestString = baseServiceUrl + "/resource/node/" + srcID + "/paths?dst=" + dstID;
				Paths paths = client.target(requestString)
						.request(MediaType.APPLICATION_XML)
						.accept(MediaType.APPLICATION_XML)
						.get(Paths.class);
			
				System.out.println("Found n paths: " + paths.getPath().size());
				
				if(paths.getPath().size()==0){
					//no paths found
					return false;
				}
				else{
					//at least one path found
					return true;
				}
			}catch(WebApplicationException e){
				System.out.println("Error " + e.getResponse().getStatus() + " Received from server.\n" + e.getMessage());
				System.out.println("Error in retrieving the paths");
				throw new ServiceException(e);
			
			}catch(Exception e){
				System.out.println("Error in retrieving the paths");
				e.printStackTrace();
				throw new ServiceException(e);
			}
			
	}

	@Override
	public String getCurrentGraphName() {
		return graphName;
	}
	
	private void extractInformationOfNffgForDB(NffgReader nffg) throws ServiceException{
		nodeNames =new LinkedHashMap<String,Node>();
	
		//NODES loading
		for(NodeReader nr: nffg.getNodes()){
			//System.out.println("Node name:\t" + nr.getName());
			Node node = new Node();
			Property nodeProperty = new Property();
			nodeProperty.setName("name");
			nodeProperty.setValue(nr.getName());
			node.getProperty().add(nodeProperty);
			Node response=null;
			try{
					String resourceName = baseServiceUrl + "/resource/node/";
					response = client.target(resourceName)
							.request(MediaType.APPLICATION_XML)
							.accept(MediaType.APPLICATION_XML)
							.post(Entity.xml(node),Node.class);
					//System.out.println("Response of server: \t" + response.getId() + response.getProperty().get(0).getValue());
			
			}catch(WebApplicationException e){
				System.out.println("Error " + e.getResponse().getStatus() + " Received from server.\n" + e.getMessage());
				System.out.println("Bad Response from the server");
				throw new ServiceException(e);
			}catch(Exception e){
				System.out.println("Bad Response from the server");
				e.printStackTrace();
				throw new ServiceException(e);
			}
			//indexes the node with their symbolic name (not the database id)
			nodeNames.put(response.getProperty().get(0).getValue(),response); 
				
		}
		
		//RELATIONSHIPS - LINKS		
		for(NodeReader nr : nffg.getNodes()){
			for(LinkReader lr : nr.getLinks()){
				String srcId = (nodeNames.get(lr.getSourceNode().getName())).getId();
				String dstId = (nodeNames.get(lr.getDestinationNode().getName())).getId(); 
			
				//prepare relationship for POST
				Relationship relationship = new Relationship();
				relationship.setDstNode(dstId);
				relationship.setSrcNode(srcId);
				
				//This name is set by Assignment2.pdf
				relationship.setType("Link");
				
				String requestString = baseServiceUrl + "/resource/node/" + srcId +"/relationship";
				try{
					Relationship returnedRelationship =
							client.target(requestString)
							.request(MediaType.APPLICATION_XML)
							.accept(MediaType.APPLICATION_XML)
							.post(Entity.xml(relationship),Relationship.class);
					
					//System.out.println("Returned Relationship: " + returnedRelationship.getId() + " " + returnedRelationship.getSrcNode() + " " + returnedRelationship.getDstNode() + " " + returnedRelationship.getType());
				}catch(WebApplicationException e){
					System.out.println("Error " + e.getResponse().getStatus() + " Received from server.\n" + e.getMessage());
					System.out.println("Error in creating the relationship");
					throw new ServiceException(e);
				}catch(Exception e){
					System.out.println("Error in creating the relationship");
					e.printStackTrace();
					throw new ServiceException(e);
				}
				
			}
		}
	}
	

}
