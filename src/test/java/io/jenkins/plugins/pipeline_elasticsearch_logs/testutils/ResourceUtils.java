package io.jenkins.plugins.pipeline_elasticsearch_logs.testutils;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;

import org.apache.commons.io.FileUtils;

import net.sf.json.JSONArray;

/**
 * Helper to handle resource files corresponding to specific test methods. 
 */
public class ResourceUtils
{

  /**
   * Returns the content of the {@code <testMethod>.Jenkinsfile} corresponding to the test method
   * which (indirectly) called this method.
   * @return
   * @throws IOException
   * @throws SecurityException
   * @throws ClassNotFoundException
   */
  public static String getTestPipeline() throws IOException, SecurityException, ClassNotFoundException {
    return readResource(getTestResourcePath("Jenkinsfile"));
  }

  /**
   * Returns the content of the {@code <testMethod>.log} file corresponding to the test method
   * which (indirectly) called this method.
   * @return
   * @throws IOException
   * @throws SecurityException
   * @throws ClassNotFoundException
   */
  public static String getExpectedTestLog() throws IOException, SecurityException, ClassNotFoundException {
    return readResource(getTestResourcePath("log"));
  }

  /**
   * Returns the content of the {@code <testMethod>.log.json} file corresponding to the test method
   * which (indirectly) called this method.
   * @return The JSONArray contained in the .log.json file.
   * @throws IOException
   * @throws SecurityException
   * @throws ClassNotFoundException
   */
  public static JSONArray getExpectedTestJsonLog() throws IOException, SecurityException, ClassNotFoundException {
    return JSONArray.fromObject(readResource(getTestResourcePath("log.json")));
  }

  /**
   * Returns the resource path of the file, corresponding to the test method
   * which (indirectly) calls this method, with the specified fileExtension.
   * @param fileExtension
   * @return
   * @throws SecurityException
   * @throws ClassNotFoundException
   */
  private static String getTestResourcePath(String fileExtension) throws SecurityException, ClassNotFoundException {
    Method method = getTestMethod();
    return String.format("%s/%s.%s",
          method.getDeclaringClass().getName().replace(".", "/"),
          method.getName().replace(".", "/"),
          fileExtension);    
  }
  
  /**
   * Identifies the calling test method
   * @return
   * @throws SecurityException
   * @throws ClassNotFoundException
   */
  private static Method getTestMethod() throws SecurityException, ClassNotFoundException {
    for(StackTraceElement stackElement : new RuntimeException().getStackTrace()) {
      String className = stackElement.getClassName();
      for(Method method : Class.forName(className).getDeclaredMethods()) {
        if(stackElement.getMethodName().equals(method.getName())) {
          for(Annotation annotation : method.getDeclaredAnnotations()) {
            if(annotation instanceof org.junit.Test) {
              return method;
            }
          }
        }
      }
    }
    throw new IllegalStateException("No test method found. Not called from a test?");
  }

  /**
   * Reads the file content into a String
   * @param filePath
   * @return the content as String
   * @throws IOException
   */
  private static String readResource(String filePath) throws IOException {
    ClassLoader classLoader = ResourceUtils.class.getClassLoader();
    URL resource = classLoader.getResource(filePath);
    if(resource == null) throw new IOException("Could not find resource: " + filePath);
    File file = new File(resource.getFile());
    return FileUtils.readFileToString(file, "UTF-8");
  }

}
