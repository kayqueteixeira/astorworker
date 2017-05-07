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

import org.apache.log4j.Logger;

import fr.inria.astor.core.validation.entity.TestResult;
import br.ufg.inf.astorworker.entity.Project;

/**
 * Process-based program variant validation
 * 
 * @author Matias Martinez, matias.martinez@inria.fr
 * 
 */
public class  JUnitTestExecutorProcess {
	private final static String OUTSEP = "mmout";
	private Logger logger = Logger.getLogger(JUnitTestExecutorProcess.class);
	
	boolean avoidInterruption = false;
	
	public JUnitTestExecutorProcess(boolean avoidInterruption ) {
		this.avoidInterruption = avoidInterruption;
	}
	
	public TestResult execute(Project project, File variant, List<String> classesToExecute, int waitTime) throws MalformedURLException {
		return execute("/usr/lib/jvm/java-8-oracle/bin", project, variant, classesToExecute, waitTime);
	}

	public TestResult execute(String jvmPath, Project project, File variant, List<String> classesToExecute, int waitTime) throws MalformedURLException {
		jvmPath += File.separator + "java";
		String systemcp = defineInitialClasspath();

		String classpath = systemcp + File.pathSeparator + urlArrayToString(createClassPath(project, variant));

		List<String> cls = new ArrayList<>(classesToExecute);

		try {

			List<String> command = new ArrayList<String>();
			command.add(jvmPath);
			command.add("-Xmx2048m");
			command.add("-cp");
			command.add(classpath);
			command.add(classNameToCall());
			command.addAll(cls);

			logger.debug("Command:");
			logger.debug("JUnitExecutorProcess:");
			logger.debug(String.join(" ", command));

			List<String> output = CommandExecutorProcess.execute(String.join(" ", command), project.getParentPath());
			
		
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
		try {
			for(String line : output) {
				out += line + "\n";
				if (line.startsWith(OUTSEP)) {
					String[] s = line.split(OUTSEP);
					int nrtc = Integer.valueOf(s[1]);
					tr.casesExecuted = nrtc;
					int failing = Integer.valueOf(s[2]);
					tr.failures = failing;
					if (!"".equals(s[3])) {
						String[] falinglist = s[3].replace("[", "").replace("]", "").split(",");
						for (String string : falinglist) {
							if (!string.trim().isEmpty())
								tr.failTest.add(string.trim());
						}
					}
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

	public String classNameToCall(){
		return	("JUnitExternalExecutor");
		//return JUnitTestExecutor.class.getName();
	}

	public String defineInitialClasspath(){
		return (new File("./lib/jtestex-0.0.1.jar").getAbsolutePath());
	}

	protected String urlArrayToString(URL[] urls) {
		String s = "";
		for (int i = 0; i < urls.length; i++) {
			URL url = urls[i];
			s += url.getPath() + File.pathSeparator;
		}
		return s;
	}

	protected String getProcessError(InputStream str){
		String out = "";
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(str));
			String line;
			while ((line = in.readLine()) != null) {
				out += line + "\n";
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return out;
	}
	
	private URL[] createClassPath(Project project, File variant) throws MalformedURLException {
		URL[] defaultSUTClasspath = getClassPathURLforProject(project);
		List<URL> originalURL = new ArrayList(Arrays.asList(defaultSUTClasspath));

		String classpath = System.getProperty("java.class.path");

		for (String path : classpath.split(File.pathSeparator)) {

			File f = new File(path);
			originalURL.add(new URL("file://" + f.getAbsolutePath()));

		}

		return redefineURL(variant, originalURL.toArray(new URL[0]));
	}

	public URL[] getClassPathURLforProject(Project project) throws MalformedURLException {
			String[] dependencies = project.getDependencies().split(":");
			List<URL> classpath = new ArrayList<URL>();
			for(String dependency : dependencies){
				dependency = dependency.replace("./","");
				classpath.add(new URL("file://" + dependency.trim()));
			}
				

			// bin
			URL urlBin = new File("workDir/AstorWorker-"+project.getProjectName()+"/default/bin").toURI().toURL();
			classpath.add(urlBin);
	
			URL[] cp = classpath.toArray(new URL[0]);
			return cp;
	}

	public static URL[] redefineURL(File foutgen, URL[] originalURL) throws MalformedURLException {
		List<URL> urls = new ArrayList<URL>();
		urls.add(foutgen.toURL());
		for (int i = 0; (originalURL != null) && i < originalURL.length; i++) {
			urls.add(originalURL[i]);
		}

		return (URL[]) urls.toArray(originalURL);
	}

}
