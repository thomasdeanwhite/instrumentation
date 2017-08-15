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

    public static final String NAME = "Sytche";

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
    }

    public enum InstrumentationApproach {
        STATIC, ARRAY, NONE
    }

    @Parameter(key = "log_filename", description = "Select the file name for the log file. Files are divided into folders for coverage etc", category = "Instrumentation")
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

    @Parameter(key = "write_class", description = "flag to determine whether or not to write classes. If set to true, the InstrumentingClassLoader will write out all classes to the value of BYTECODE_DIR", hasArgs = true, category = "Instrumentation")
    public static boolean WRITE_CLASS = false;

    @Parameter(key = "bytecode_dir", description = "directory in which to store bytecode if the WRITE_CLASS property is set to true", hasArgs = true, category = "Instrumentation")
    public static String BYTECODE_DIR = System.getProperty("user.home") + "/.bytecode/";

    @Parameter(key = "log_dir", description = "directory in which to store log files (application.log, timings.log)", hasArgs = true, category = "Instrumentation")
    public static String LOG_DIR = System.getProperty("user.home") + "/.logs/";

    @Parameter(key = "log_timings", description = "set whether application timings should be written to a log file", hasArgs = true, category = "Instrumentation")
    public static boolean LOG = true;

    @Parameter(key = "use_changed_flag", description = "It is possible to add a flag through instrumentation that will tell the ClassAnalyzer that a class has changed in some way. This creates a form of hybrid approach to instrumentation, but saves work at the time of collecting coverage data", hasArgs = true, category = "Instrumentation")
    public static boolean USE_CHANGED_FLAG = true;

    @Parameter(key = "track_active_testcase", description = "When collecting coverage information, it is possible to include information about which test case covered each line. If this argument is true, use ClassAnalyzer.setActiveTest(TestCase), and then each line/branch object will have a list of test cases that cover it, accessed by CoverableGoal.getCoveringTests", hasArgs = true, category = "Instrumentation")
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
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void setParameter(String key, String value) throws IllegalArgumentException, IllegalAccessException {
        if (!parameterMap.containsKey(key)) {
            throw new IllegalArgumentException(key + " was not found in the InstrumentationProperties class");
        }
        Field f = parameterMap.get(key);
        Class<?> cl = f.getType();
        if (cl.isAssignableFrom(Number.class) || cl.isPrimitive()) {
            if (cl.equals(Long.class) || cl.equals(long.class)) {
                try {
                    Long l = Long.parseLong(value);
                    f.setLong(null, l);
                } catch (NumberFormatException e) {
                    Double fl = Double.parseDouble(value);
                    f.setLong(null, (long) fl.doubleValue());
                }
            } else if (cl.equals(Double.class) || cl.equals(double.class)) {
                Double d = Double.parseDouble(value);
                f.setDouble(null, d);
            } else if (cl.equals(Float.class) || cl.equals(float.class)) {
                Float fl = Float.parseFloat(value);
                f.setFloat(null, fl);
            } else if (cl.equals(Integer.class) || cl.equals(int.class)) {
                Double fl = Double.parseDouble(value);
                f.setInt(null, (int) fl.doubleValue());
            } else if (cl.equals(Boolean.class) || cl.equals(boolean.class)) {
                Boolean bl = Boolean.parseBoolean(value);
                f.setBoolean(null, bl);
            }
        } else if (cl.isAssignableFrom(String.class)) {
            f.set(null, value);
        }
        if (f.getType().isEnum()) {
            f.set(null, Enum.valueOf((Class<Enum>) f.getType(), value.toUpperCase()));
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