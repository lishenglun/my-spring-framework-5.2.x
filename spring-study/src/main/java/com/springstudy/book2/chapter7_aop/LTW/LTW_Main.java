package com.springstudy.book2.chapter7_aop.LTW;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.weaving.AspectJWeavingEnabler;
import org.springframework.context.weaving.DefaultContextLoadTimeWeaver;
import org.springframework.context.weaving.LoadTimeWeaverAwareProcessor;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/13 4:46 下午
 */
public class LTW_Main {

	/**
	 * bug1：
	 *
	 * 警告: Exception encountered during context initialization - cancelling refresh attempt: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'loadTimeWeaver': Initialization of bean failed; nested exception is java.lang.IllegalStateException: ClassLoader [sun.misc.Launcher$AppClassLoader] does NOT provide an 'addTransformer(ClassFileTransformer)' method. Specify a custom LoadTimeWeaver or start your Java virtual machine with Spring's agent: -javaagent:spring-instrument-{version}.jar
	 * Exception in thread "main" org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'loadTimeWeaver': Initialization of bean failed; nested exception is java.lang.IllegalStateException: ClassLoader [sun.misc.Launcher$AppClassLoader] does NOT provide an 'addTransformer(ClassFileTransformer)' method. Specify a custom LoadTimeWeaver or start your Java virtual machine with Spring's agent: -javaagent:spring-instrument-{version}.jar
	 *
	 * 解决：加入如下VM options即可
	 *
	 * -javaagent:/Users/lishenglun/word/theSourceCode/spring-framework-5.2.x/spring-study/src/main/resources/spring-instrument-5.2.9.RELEASE.jar
	 */

	/**
	 * 一、Spring中的静态AOP
	 *
	 * （1）Spring中的静态AOP直接使用了AspectJ提供的方法(LTW)，也就是将动态代理的任务直接委托给了AspectJ；
	 * （2）而AspectJ又是在Instrument基础上进行的封装（Instrument指java.lang.instrument包）；
	 *
	 * 题外：AspectJ会读取META-INF/aop.xml中配置的增强器，然后通过AspectJ内部自定义的ClassFileTransformer，织入到对于的类中！
	 *
	 * （3）使用JDK5新增的java.lang.instrument包，在类加载时对字节码进行转换，从而实现AOP功能。
	 *
	 * 题外：java.lang.instrument包：JDK5新增的。它类似一种更低级、更松耦合的AOP，可以在类加载时对字节码进行转换，来改变一个类的行为，从而实现AOP功能。相当于在JVM层面做了AOP支持。
	 * >>> 通过java.lang.instrument包实现agent，使得"监控代码"和"应用代码"完全隔离了。
	 *
	 * 二、Spring如何嵌入AspectJ的
	 *
	 * Spring是直接使用AspectJ，也就是将动态代理的任务直接委托给了AspectJ，AspectJ所做的事情，并不在我们讨论的范畴，我们只关注Spring怎么嵌入AspectJ的。以下是Spring怎么嵌入AspectJ的具体过程。
	 *
	 * 1、首先是在配置文件中，配置<context:load-time-weaver/>标签，代表Spring中开启使用AspectJ功能，也是一个️从动态代理的方式改成静态代理的方式。
	 *
	 * 2、由参考{@link org.springframework.context.config.ContextNamespaceHandler}得知，
	 * <context:load-time-weaver/>标签的解析器是{@link org.springframework.context.config.LoadTimeWeaverBeanDefinitionParser}
	 *
	 * 3、在LoadTimeWeaverBeanDefinitionParser解析<context:load-time-weaver>标签时，一共注册2个bd：
	 * 解析标签的时候，会注册一个bean；同时对于标签本身，spring也会以bean的形式保存
	 * （1）org.springframework.context.config.internalAspectJWeavingEnabler = {@link AspectJWeavingEnabler}
	 *
	 * 题外：AspectJWeavingEnabler implements LoadTimeWeaverAware
	 *
	 * （2）loadTimeWeaver = {@link DefaultContextLoadTimeWeaver}
	 *
	 * 题外：DefaultContextLoadTimeWeaver implements LoadTimeWeaver
	 *
	 * 4、由于注册了 loadTimeWeaver = {@link DefaultContextLoadTimeWeaver}，
	 * 所以在{@link org.springframework.context.support.AbstractApplicationContext#prepareBeanFactory(ConfigurableListableBeanFactory)}中，
	 * 会添加一个BeanPostProcessor：{@link LoadTimeWeaverAwareProcessor}，正是因为注册了LoadTimeWeaverAwareProcessor，所以才激活了整个AspectJ的功能！
	 *
	 * 5、在实例化AspectJWeavingEnabler时，会经由{@link LoadTimeWeaverAwareProcessor#postProcessBeforeInitialization(Object, String)}处理，
	 * 里面会获取DefaultContextLoadTimeWeaver，设置到AspectJWeavingEnabler.loadTimeWeaver属性上
	 *
	 * 6、如果在获取DefaultContextLoadTimeWeaver时，还未创建，则会创建DefaultContextLoadTimeWeaver。
	 * 在创建DefaultContextLoadTimeWeaver过程中，由于DefaultContextLoadTimeWeaver实现了BeanClassLoaderAware，
	 * 所以会调用{@link DefaultContextLoadTimeWeaver#setBeanClassLoader(ClassLoader)}，
	 * 在里面，会⚠️实例化InstrumentationLoadTimeWeaver作为DefaultContextLoadTime.loadTimeWeaver属性，
	 * 而在实例化InstrumentationLoadTimeWeaver的构造器中，里面初始化了Instrumentation实例：代表当前虚拟机的实例。对于注册转换器，如addTransformer()等，便可以直接使用此属性进行操作了。
	 *
	 * 7、AspectJWeavingEnabler还实现了BeanFactoryPostProcessor，在{@link AspectJWeavingEnabler#postProcessBeanFactory(ConfigurableListableBeanFactory)}中，
	 * 会通过DefaultContextLoadTime.loadTimeWeaver属性，也就是Instrumentation，注册转换器。转换器 = {@link AspectJWeavingEnabler.AspectJClassBypassingClassFileTransformer}
	 *
	 * 题外：AspectJClassBypassingClassFileTransformer implements ClassFileTransformer
	 *
	 * 8、AspectJClassBypassingClassFileTransformer最主要的作用：告诉AspectJ：以org.aspectj开头的或者org/aspectj开头的类，不进行编织处理；然后还是委托给AspectJ代理继续处理
	 *
	 * 9、至此Spring部分完毕，剩下的交给AspectJ来做！
	 */
	public static void main(String[] args) {
		ApplicationContext ac = new ClassPathXmlApplicationContext("book2/chapter7_aop/aspectj-LTW.xml");
		TestBean test = (TestBean) ac.getBean("test");
		test.test();
	}

}