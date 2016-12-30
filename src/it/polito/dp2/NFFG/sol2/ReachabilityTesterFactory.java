package it.polito.dp2.NFFG.sol2;

import it.polito.dp2.NFFG.lab2.ReachabilityTester;
import it.polito.dp2.NFFG.lab2.ReachabilityTesterException;

public class ReachabilityTesterFactory extends it.polito.dp2.NFFG.lab2.ReachabilityTesterFactory{

	
	@Override
	public ReachabilityTester newReachabilityTester() throws ReachabilityTesterException {
		ReachabilityTester reachTester=null;
		try{
			reachTester = new ConcreteReachabilityTester();
		}catch(Exception e){
			throw new ReachabilityTesterException(e);
		}
		
		return reachTester;
	}

}
