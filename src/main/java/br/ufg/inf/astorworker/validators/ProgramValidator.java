package br.ufg.inf.astorworker.validators;

import java.io.File;
import java.util.Arrays;
import java.net.MalformedURLException;

import org.apache.log4j.Logger;

import fr.inria.astor.core.validation.entity.TestResult;
import fr.inria.astor.core.validation.validators.TestCasesProgramValidationResult;
import br.ufg.inf.astorworker.executors.InstrumentationTestExecutorProcess;
import br.ufg.inf.astorworker.executors.JUnitTestExecutorProcess; 
import br.ufg.inf.astorworker.entity.Project; 

public class ProgramValidator  {
	private static Logger logger = Logger.getLogger(ProgramValidator.class);
	
	public static TestCasesProgramValidationResult validate(Project project, File variant, int waitTime) throws MalformedURLException {
		TestResult tr = null;

		//Executing normal test cases
		if(project.getFailing() != null) {
			JUnitTestExecutorProcess jtep = new JUnitTestExecutorProcess(false);
			tr = jtep.execute(project, variant, Arrays.asList(project.getFailing().split(":")),waitTime);

			if(tr == null){
				//There was an error validating the variant
				return new TestCasesProgramValidationResult(true);
			}
		}
					

		//Executing instrumentation test cases
	 	if(project.getInstrumentationFailing() != null){
			InstrumentationTestExecutorProcess itep = new InstrumentationTestExecutorProcess();
			tr = itep.execute(tr, project, Arrays.asList(project.getInstrumentationFailing().split(":")), variant, waitTime);

			if(tr == null){
				//There was an error validating the variant
				return new TestCasesProgramValidationResult(true);
			}
		}

		logger.info("Number of test cases executed: "+tr.casesExecuted+"\tNumber of failing test cases: "+tr.failures);

		if(tr.wasSuccessful()){
			logger.info("No failing test cases");
			//Execute regression
			return runRegression(project, variant, waitTime);
		}

		return new TestCasesProgramValidationResult(tr, tr.wasSuccessful(), false);
	}


	private static TestCasesProgramValidationResult runRegression(Project project, File variant, int waitTime) throws MalformedURLException {
		logger.info("Running regression");
		TestResult trregression = null;

		//Executing normal test cases
		if(project.getRegressionTestCases() != null) {
			JUnitTestExecutorProcess jtep = new JUnitTestExecutorProcess(false);
			trregression = jtep.execute(project, variant, Arrays.asList(project.getRegressionTestCases().split(":")),waitTime);

			if(trregression == null){
				//There was an error validating the variant
				return new TestCasesProgramValidationResult(true);
			}
		}

		//Executing instrumentation test cases
	 	if(project.getInstrumentalRegressionTestCases() != null){
			InstrumentationTestExecutorProcess itep = new InstrumentationTestExecutorProcess();
			trregression = itep.execute(trregression, project, Arrays.asList(project.getInstrumentalRegressionTestCases().split(":")), variant, waitTime);

			if(trregression == null){
				//There was an error validating the variant
				return new TestCasesProgramValidationResult(true);
			}
		}

		logger.info("Number of test cases executed: "+trregression.casesExecuted+"\tNumber of failing test cases: "+trregression.failures);
		
		return new TestCasesProgramValidationResult(trregression, trregression.wasSuccessful(), true);			
	}
}