package br.ufg.inf.astorworker.executors;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.log4j.Logger;

import fr.inria.astor.core.validation.entity.TestResult;
import br.ufg.inf.astorworker.entities.AndroidProject;

/**
 * Process-based program variant validation
 * 
 * @author Matias Martinez, matias.martinez@inria.fr
 * 
 */
public class  JUnitTestExecutorProcess {
	private Logger logger = Logger.getLogger(JUnitTestExecutorProcess.class);
	
	public TestResult execute(AndroidProject project, List<String> classesToExecute) {
		try {
			List<String> output = AndroidToolsExecutorProcess.runUnitTests(project.getLocation(), project.getUnitTestTask(), classesToExecute);
		
			TestResult tr = getTestResult(output);
			
			return tr;
		} catch ( IOException |InterruptedException |IllegalThreadStateException  ex) {
			logger.error("The Process that runs JUnit test cases had problems: " + ex.getMessage());
		}
		return null;
	}

	public TestResult executeRegression(AndroidProject project) {
		try {
			List<String> output = AndroidToolsExecutorProcess.runUnitTests(project.getLocation(), project.getUnitTestTask());
		
			TestResult tr = getTestResult(output);
			
			return tr;
		} catch ( IOException |InterruptedException |IllegalThreadStateException  ex) {
			logger.error("The Process that runs JUnit test cases had problems: " + ex.getMessage());
		}
		return null;
	}

	private TestResult getTestResult(List<String> output) {
		TestResult tr = new TestResult();
		boolean success = false;
		String out = "";
		Pattern testPattern = Pattern.compile("([a-zA-Z0-9._])+(\\s>\\s)([a-zA-Z0-9._])+(\\s)(FAILED|PASSED)\\s*");
		try {
			for(String line : output) {
				out += line + "\n";
				Matcher m = testPattern.matcher(line);

				if (m.matches()) {
					tr.casesExecuted++;

					String result = m.group(5);
					if(result.equals("FAILED"))
						tr.failures++;
					success = true;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (success)
			return tr;
			

		else {
			logger.error("Error reading the validation process, output:\n\t"+out);
			return null;
		}
	}

}
