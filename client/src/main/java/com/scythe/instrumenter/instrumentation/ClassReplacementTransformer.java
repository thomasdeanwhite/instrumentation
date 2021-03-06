package com.scythe.instrumenter.instrumentation;

import com.scythe.instrumenter.InstrumentationProperties;
import com.scythe.instrumenter.InstrumentationProperties.InstrumentationApproach;
import com.scythe.instrumenter.analysis.ClassAnalyzer;
import com.scythe.instrumenter.analysis.InstrumentingTask;
import com.scythe.instrumenter.analysis.task.Task;
import com.scythe.instrumenter.analysis.task.TaskTimer;
import com.scythe.util.ClassNameUtils;
import com.scythe.util.Util;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.IllegalClassFormatException;
import java.util.ArrayList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;

public class ClassReplacementTransformer {

  private static final ArrayList<String> forbiddenPackages = new ArrayList<String>();
  private static ArrayList<String> seenClasses = new ArrayList<String>();

  static {
    String[] defaultHiddenPackages = new String[]{"com/sun", "java/", "sun/", "jdk/",
        "com/scythe/instrumenter", "org/eclipse"};

    // String[] defaultHiddenPackages = new
    // String[]{"com/scythe/leapmotion", "com/google/gson",
    // "com/sun", "java/", "sun/", "com/leapmotion", "jdk/", "javax/",
    // "org/json", "org/apache/commons/cli",
    // "com/scythe/instrumenter", "com/dpaterson", "org/junit"};

    for (String s : defaultHiddenPackages) {
      forbiddenPackages.add(s);
    }
  }

  public static ArrayList<ShouldInstrumentChecker> shouldInstrumentCheckers = new ArrayList<ShouldInstrumentChecker>();
  private boolean shouldWriteClass = false;

  public ClassReplacementTransformer() {
    shouldInstrumentCheckers.add(new ShouldInstrumentChecker() {
      @Override
      public boolean shouldInstrument(String className) {
        return InstrumentationProperties.INSTRUMENTATION_APPROACH != InstrumentationApproach.NONE;
      }
    });

    shouldInstrumentCheckers.add(new ShouldInstrumentChecker() {
      @Override
      public boolean shouldInstrument(String className) {
        if (className == null) {
          return false;
        }
        if (isForbiddenPackage(className)) {
          return false;
        }
        return true;
      }
    });
  }

  /**
   * Add a package that should not be instrumented.
   *
   * @param forbiddenPackage the package name not to be instrumented, using / for subpackages (e.g. org/junit)
   */
  public static void addForbiddenPackage(String forbiddenPackage) {
    forbiddenPackages.add(forbiddenPackage);
  }

  public static boolean isForbiddenPackage(String clazz) {
    for (String s : forbiddenPackages) {
      if (ClassNameUtils.standardise(clazz).startsWith(ClassNameUtils.standardise(s))) {
        return true;
      }
    }
    return false;
  }

  public void setWriteClasses(boolean b) {
    shouldWriteClass = b;
  }

  public byte[] transform(String cName, byte[] cBytes, ClassVisitor cv, ClassWriter cw)
      throws IllegalClassFormatException {
    if (seenClasses.contains(cName)) {
      throw new IllegalClassFormatException("Class already loaded!");
    }
    seenClasses.add(cName);

    // if (InstrumentationProperties.EXILED_CLASSES != null) {
    // for (String s : Properties.EXILED_CLASSES) {
    // if (cName.equals(s)) {
    // // App.out.println("Not loaded class " + cName);
    // throw new IllegalClassFormatException();
    // }
    // }
    // }

    // App.out.println("Loaded class " + cName);
    try {
      if (!shouldInstrumentClass(cName)) {
        return cBytes;
      }

      // if (iClass == null) {
      // iClass = TestingClassLoader.getClassLoader().loadClass(cName,
      // cBytes);
      // }

      // iClass.

      InputStream ins = new ByteArrayInputStream(cBytes);
      byte[] newClass = cBytes;
      try {
        ClassReader cr = new ClassReader(ins);
        Task instrumentingTask = new InstrumentingTask(cName);
        if (InstrumentationProperties.LOG) {
          TaskTimer.taskStart(instrumentingTask);
        }
        // Handle JSR instructions
        cv = new ClassVisitor(Opcodes.ASM5, cv) {
          @Override
          public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                           String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);
          }
        };
        try {
          cr.accept(cv, ClassReader.EXPAND_FRAMES);
        } catch (Throwable t) {
          t.printStackTrace(ClassAnalyzer.out);
        }
        newClass = cw.toByteArray();

        if (InstrumentationProperties.LOG) {
          TaskTimer.taskEnd(instrumentingTask);
        }
      } catch (IOException e) {
        e.printStackTrace(ClassAnalyzer.out);
      }
      if (InstrumentationProperties.WRITE_CLASS_IF_MODIFIED && !InstrumentationProperties.WRITE_CLASS) {
        Util.writeClass(cName, newClass);
      }
      return newClass;
    } catch (Exception e) {
      e.printStackTrace(ClassAnalyzer.out);
      return cw.toByteArray();

    }

  }

  public boolean shouldInstrumentClass(String className) {
    className = ClassNameUtils.standardise(className);

    for (ShouldInstrumentChecker sic : shouldInstrumentCheckers) {
      if (!sic.shouldInstrument(className)) {
        return false;
      }
    }

    return true;
  }

  public static void addShouldInstrumentChecker(ShouldInstrumentChecker s) {
    shouldInstrumentCheckers.add(s);
  }

  public static void removeShouldInstrumentChecker(ShouldInstrumentChecker s) {
    shouldInstrumentCheckers.remove(s);
  }

  public interface ShouldInstrumentChecker {
    boolean shouldInstrument(String className);
  }
}
