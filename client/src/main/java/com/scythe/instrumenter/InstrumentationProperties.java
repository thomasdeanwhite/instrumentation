package com.scythe.instrumenter;

import com.scythe.instrumenter.analysis.ClassAnalyzer;
import com.scythe.output.Csv;
import org.apache.commons.cli.*;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class InstrumentationProperties implements PropertySource {

  public static final String NAME = "Scythe";

  protected InstrumentationProperties() {
    reflectMap();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.FIELD)
  public @interface Parameter {

    String key();

    String group()
        default "Experimental";

    String description();

    boolean hasArgs()
        default true;

    String category();

    boolean hasOptions()
        default false;
  }

  public enum InstrumentationApproach {
    STATIC, ARRAY, NONE
  }

  @Parameter(key = "source_dir", description = "Source code directory",
      category = "Output")
  public static String SOURCE_DIR = "src";

  @Parameter(key = "output", description =
      "Select the file name for the serialised results. This is the beginning of a file unless %s is specified. To specify a "
          +
          "different extension than JSON, use '[filename.]%s.extension",
      category = "Output")
  public static String OUTPUT = "Scythe.%s.JSON";

  @Parameter(key = "coverage_on_exit", description = "Write coverage when system exits", category = "Instrumentation")
  public static boolean COVERAGE_ON_EXIT = false;

  @Parameter(key = "coverage_file", description = "Coverage file to write to", category = "Instrumentation")
  public static String COVERAGE_FILE = "";

  @Parameter(key = "pretty_print_coverage", description = "whether to use gson pretty printing for coverage files", category="Output")
  public static boolean PRETTY_PRINT_COVERAGE = false;

  @Parameter(key = "disable_html_escape", description = "Whether to disable HTML escaping for GSON", category="Output")
  public static boolean DISABLE_HTML_ESCAPE = false;

  @Parameter(key = "log_filename", description = "Select the file name for the log file. Files are divided into folders for coverage etc", category = "Logging")
  public static String LOG_FILENAME = "";

  @Parameter(key = "instrumentation_approach", description = "Determines the approach to be used during class instrumentation. A static approach inserts calls to ClassAnalyzer.lineFound etc to track which lines/branches have been covered. Using an array stores all line/branch executions in an array of integers and has a method to get all the values", hasArgs = true, category = "Instrumentation")
  public static InstrumentationApproach INSTRUMENTATION_APPROACH = InstrumentationApproach.ARRAY;

  @Parameter(key = "instrument_lines", description = "Switch on line instrumentation", hasArgs = true, category = "Instrumentation")
  public static boolean INSTRUMENT_LINES = true;

  @Parameter(key = "instrument_branches", description = "Switch on branch instrumentation", hasArgs = true, category = "Instrumentation")
  public static boolean INSTRUMENT_BRANCHES = true;

  @Parameter(key = "null_output", description = "Value to output to CSV for" +
      " null objects", category = "Output")
  private static final String NULL_VALUE_OUTPUT = "NA";

  @Parameter(key = "write_class", description = "flag to determine whether or not to write classes. If set to true, the InstrumentingClassLoader will write out all classes to the value of BYTECODE_DIR", hasArgs = true, category = "Dev")
  public static boolean WRITE_CLASS = false;

  @Parameter(key = "write_class_if_modified", description = "flag to write out classes that have been modified by the instrumenter. If true, changed classes will be written to the directory of BYTECODE_DIR", category = "Dev")
  public static boolean WRITE_CLASS_IF_MODIFIED = false;

  @Parameter(key = "bytecode_dir", description = "directory in which to store bytecode if the WRITE_CLASS property is set to true", hasArgs = true, category = "Dev")
  public static String BYTECODE_DIR = System.getProperty("user.home") + "/.bytecode/";

  @Parameter(key = "log_dir", description = "directory in which to store log files (application.log, timings.log)", hasArgs = true, category = "Logging")
  public static String LOG_DIR = System.getProperty("user.home") + "/.logs/";

  @Parameter(key = "log_timings", description = "set whether application timings should be written to a log file", hasArgs = true, category = "Logging")
  public static boolean LOG = true;

  @Parameter(key = "use_changed_flag", description = "It is possible to add a flag through instrumentation that will tell the ClassAnalyzer that a class has changed in some way. This creates a form of hybrid approach to instrumentation, but saves work at the time of collecting coverage data", hasArgs = true, category = "Instrumentation")
  public static boolean USE_CHANGED_FLAG = true;

  @Parameter(key = "track_active_testcase", description = "When collecting coverage information, it is possible to include information about which test case covered each line. If this argument is true, use ClassAnalyzer.setActiveTest(TestCase), and then each line/branch object will have a list of test cases that cover it, accessed by CoverableGoal.getCoveringTests", hasArgs = true, category = "Testing")
  public static boolean TRACK_ACTIVE_TESTCASE = false;

  protected Map<String, Field> parameterMap = new HashMap<String, Field>();
  protected Map<String, Parameter> annotationMap = new HashMap<String, Parameter>();
  protected Map<String, ArrayList<String>> categoryMap = new HashMap<String, ArrayList<String>>();

  private void reflectMap() {
    for (Field field : Arrays.asList(getClass().getFields())) {
      if (field.isAnnotationPresent(Parameter.class)) {
        Parameter p = field.getAnnotation(Parameter.class);
        String key = p.key();
        parameterMap.put(key, field);
        annotationMap.put(key, p);
        if (categoryMap.containsKey(p.category())) {
          categoryMap.get(p.category()).add(key);
        } else {
          ArrayList<String> cats = new ArrayList<String>();
          cats.add(key);
          categoryMap.put(p.category(), cats);
        }

      }
    }
  }

  @Override
  public boolean hasParameter(String name) {
    return parameterMap.keySet().contains(name);
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public void setParameter(String key, String value) throws IllegalAccessException {
    if (parameterMap.containsKey(key)) {
      Field f = parameterMap.get(key);
      com.scythe.util.Util.setField(f, value);
    } else {
      throw new IllegalArgumentException("Key "+key+" not found in parameter map!");
    }
  }

  @Override
  public Set<String> getParameterNames() {
    return parameterMap.keySet();
  }

  private static InstrumentationProperties instance;

  public static InstrumentationProperties instance() {
    if (instance == null) {
      instance = new InstrumentationProperties();
    }
    return instance;
  }


  public void setOptions(CommandLine cmd) throws IllegalAccessException {
    try {
      for (String s : annotationMap.keySet()) {
        Parameter p = annotationMap.get(s);
        if (p.hasArgs()) {
          String value = cmd.getOptionValue(p.key());
          if (value != null) {
            setParameter(p.key(), value);
          }
        } else {
          if (cmd.hasOption(p.key())) {
            setParameter(p.key(), Boolean.toString(true));
          }
        }
      }
    } catch (Exception e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace(ClassAnalyzer.out);
    }
  }

  public Csv toCsv() {
    Csv csv = new Csv();
    for (String s : annotationMap.keySet()) {
      Field f = parameterMap.get(s);
      Class<?> cl = f.getType();

      String value = "";
      try {

        if (cl.isAssignableFrom(Number.class) || cl.isPrimitive()) {
          if (cl.equals(Long.class) || cl.equals(long.class)) {
            value = "" + f.getLong(null);
          } else if (cl.equals(Double.class) || cl.equals(double.class)) {
            value = "" + f.getDouble(null);
          } else if (cl.equals(Float.class) || cl.equals(float.class)) {
            value = "" + f.getFloat(null);
          } else if (cl.equals(Integer.class) || cl.equals(int.class)) {
            value = "" + f.getInt(null);
          } else if (cl.equals(Boolean.class) || cl.equals(boolean.class)) {
            value = "" + f.getBoolean(null);
          }

        } else if (cl.isAssignableFrom(String.class) || f.getType().isEnum()) {
          Object o = f.get(null);
          if (o != null) {
            value = o.toString();
          } else {
            value = NULL_VALUE_OUTPUT;
          }
        }
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
      csv.add(s, value);
    }

    csv.finalize();

    return csv;
  }

  public void setOptions(String[] args) {
    try {
      Options options = new Options();

      for (String s : annotationMap.keySet()) {
        Parameter p = annotationMap.get(s);
        options.addOption(p.key(), p.hasArgs(), p.description());
      }

      CommandLineParser parser = new BasicParser();
      CommandLine cmd = null;
      try {
        cmd = parser.parse(options, args);
      } catch (UnrecognizedOptionException e) {

        ClassAnalyzer.out.println(e.getLocalizedMessage());
        System.exit(-1);
      }

      setOptions(cmd);


    } catch (Exception e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace(ClassAnalyzer.out);
    }
  }

  public void printOptions() {
    for (String s : categoryMap.keySet()) {
      ClassAnalyzer.out.println(s);
      for (String opt : categoryMap.get(s)) {
        Parameter p = annotationMap.get(opt);
        String opts = " ";
        if (p.hasArgs()) {
          opts = ":[arg] ";
        }
        ClassAnalyzer.out.println(" -" + p.key() + opts + " #" + p.description() + ".");
      }
    }
  }


  public void printOptionsMd() {

    ClassAnalyzer.out.println("# Runtime Options");
    ClassAnalyzer.out.println("| Key | Description |");
    ClassAnalyzer.out.println("| --- | --- |");
    for (String s : categoryMap.keySet()) {
      ClassAnalyzer.out.println("| **" + s + "** |  |");

      for (String opt : categoryMap.get(s)) {
        Parameter p = annotationMap.get(opt);
        String opts = " ";
        if (p.hasArgs()) {
          opts = ":[arg] ";
        }
        ClassAnalyzer.out.println("| " + p.key() + opts + " | _" + p
            .description()
            + "_ |");
      }
    }
  }
}
