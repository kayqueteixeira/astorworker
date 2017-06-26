package br.ufg.inf.astorworker.executors;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.apache.log4j.Logger;

public class JavaProjectCompiler {
    private static List<String> fileList;
    private static Logger logger = Logger.getLogger(JavaProjectCompiler.class);

    static {
        fileList  = new ArrayList<String>();
    }

    public static boolean compile(File project, String dependencies) 
    		throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
    			
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        List<String> optionList = new ArrayList<String>();
        optionList.add("-classpath");
        optionList.add(dependencies);

        Iterable<? extends JavaFileObject> compilationUnit = fileManager.getJavaFileObjectsFromStrings(generateFileList(project));
        JavaCompiler.CompilationTask task = compiler.getTask(
            null, 
            fileManager, 
            diagnostics, 
            optionList, 
            null, 
            compilationUnit);
                
        boolean compiles;

        if (task.call()) {
            compiles = true;
        } 

        else {
            logger.debug("The Process that compiles java had problems, output:");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                logger.debug("\t["+diagnostic.getKind().toString()+"]: "+diagnostic.getMessage(null));
            }
            compiles = false;
        }
        fileManager.close();

        return compiles;
    } 
              
    

    private static List<String> generateFileList(File project){
        fileList.clear();
        return generateFileList(project.getName(), project);
    }
    

    private static List<String> generateFileList(String source, File node) {
        // add file only
        if (node.isFile()) {
            fileList.add(node.toString());
        }

        if (node.isDirectory()) {
            String[] subNote = node.list();
            for (String filename: subNote) {
                generateFileList(source, new File(node, filename));
            }
        }

        return fileList;
    }

}