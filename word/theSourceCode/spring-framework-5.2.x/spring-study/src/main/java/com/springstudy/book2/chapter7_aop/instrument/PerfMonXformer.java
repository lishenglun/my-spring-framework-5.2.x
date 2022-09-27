package com.springstudy.book2.chapter7_aop.instrument;

import javassist.*;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * ClassFileTransformer类
 *
 * 计算一个方法所花的时间。通常我们会在代码中硬编写，每个方法中写入重复的代码；好一点的情况，你可以用AOP来做这事，但总是感觉有点别扭。
 * 这种profiler的代码还是要打包在你的项目中，Java Instrument(java.lang.instrument包)使得这一切更干净，通过java.lang.instrument实现agent，使得监控代码和应用代码完全隔离了
 *
 * 题外：profiler：多数情况下是指用来测定你所编写的应用程序的运行效率的一个程序，它可以列出你程序中每个函数运行了多长时间等参数
 * 题外：ClassFileTransformer和Instrumentation都属于java.lang.instrument包下的
 */
public class PerfMonXformer implements ClassFileTransformer {

	/**
	 * The implementation of this method may transform the supplied class file and
	 * return a new replacement class file.
	 * <p>
	 * 此方法的实现可能会转换提供的类文件并返回一个新的替换类文件。
	 */
	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer)
			throws IllegalClassFormatException {
		byte[] transformed = null;
		System.out.println("Transforming " + className);
		ClassPool pool = ClassPool.getDefault();
		CtClass cl = null;
		try {
			cl = pool.makeClass(new ByteArrayInputStream(classfileBuffer));
			if (!cl.isInterface()) {
				CtBehavior[] methods = cl.getDeclaredBehaviors();
				for (int i = 0; i < methods.length; i++) {
					if (methods[i].isEmpty() == false) {
						doMethod(methods[i]);
					}
				}
				transformed = cl.toBytecode();
			}
		} catch (Exception e) {
			System.err.println("Could not instrument " + className + ", exception:" + e.getMessage());
		} finally {
			if (cl != null) {
				cl.detach();
			}
		}
		return transformed;
	}

	// 通过改变类的字节码，在每个类的方法入口中加入了long stime = System.nanoTime()，
	// 在方法的出口加入了: System.out.println("leave" + method.getName() + " and time:"+(System.nanoTime()-stime));
	private void doMethod(CtBehavior method) throws NotFoundException, CannotCompileException {

		method.insertBefore("long stime = System.nanoTime();");
		method.insertAfter("System.out.println(\"leave" + method.getName() + " and time:\"+(System.nanoTime()-stime));");
	}

}