package com.springstudy.book2.chapter7_aop.LTW;

/**
 * 1、这个包是关于Spring AOP LoadTimeWeaving (LTW)的，也就是Spring中的静态AOP
 *
 * 	（1）Spring中的静态AOP直接使用了AspectJ提供的方法(LTW)，也就是将动态代理的任务直接委托给了AspectJ；
 * 	（2）而AspectJ又是在Instrument基础上进行的封装（Instrument指java.lang.instrument包）；
 * 	（3）使用JDK5新增的java.lang.instrument包，在类加载时对字节码进行转换，从而实现AOP功能。
 *
 * 	参考：《Spring源码深度解析（第2版）》
 *
 * 2、AspectJ的LTW原理
 *
 * 在类加载期，通过字节码编辑技术，对类字节码进行转换，将切面织入目标类，这种方式叫做LTW（Load Time Weaving）。
 *
 * 参考：《详解 Spring AOP LoadTimeWeaving (LTW)》：https://blog.csdn.net/c39660570/article/details/106791365/
 */

/*

涉及到的东西：

1、aspectj-LTW.xml里面添加AspectJ LWT的支持

<context:load-time-weaver/>

2、aop.xml，告诉AspectJ需要对哪个包进行织入，并使用哪些增强器

3、确保classPath下有spring-instrument-5.2.9.RELEASE.jar，然后加入Vm options

-javaagent:/Users/lishenglun/word/theSourceCode/spring-framework-5.2.x/spring-study/src/main/resources/spring-instrument-5.2.9.RELEASE.jar

否则会出现如下bug：
警告: Exception encountered during context initialization - cancelling refresh attempt: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'loadTimeWeaver': Initialization of bean failed; nested exception is java.lang.IllegalStateException: ClassLoader [sun.misc.Launcher$AppClassLoader] does NOT provide an 'addTransformer(ClassFileTransformer)' method. Specify a custom LoadTimeWeaver or start your Java virtual machine with Spring's agent: -javaagent:spring-instrument-{version}.jar
Exception in thread "main" org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'loadTimeWeaver': Initialization of bean failed; nested exception is java.lang.IllegalStateException: ClassLoader [sun.misc.Launcher$AppClassLoader] does NOT provide an 'addTransformer(ClassFileTransformer)' method. Specify a custom LoadTimeWeaver or start your Java virtual machine with Spring's agent: -javaagent:spring-instrument-{version}.jar

4、LTW_Main

 */







