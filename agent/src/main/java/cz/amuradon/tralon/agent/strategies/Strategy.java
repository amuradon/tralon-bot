package cz.amuradon.tralon.agent.strategies;

public interface Strategy {
	
	void start();

	void stop();
	
	String getDescription();
	
	String link();
}
