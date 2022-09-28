package com.springstudy.msb.other.import_test;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/27 21:47
 */
public class ImportTestMain {

	// B会被作为配置类进行处理，所以会识别B上面的注解，发现有@Import(C.class)，所以就处理了C
	public static void main(String[] args) {
		test1();
		//test2();
	}

	/**
	 * 一、测试：DeferredImportSelector执行顺序
	 *
	 * 1、DeferredImportSelector源码流程执行顺序：
	 * （1）DeferredImportSelector#getImportGroup()
	 * （2）DeferredImportSelector#getExclusionFilter()
	 * （3）DeferredImportSelector.Group#process()；
	 * >>> 如果Group为null，那么执行的是DefaultDeferredImportSelectorGroup#process()，里面执行了DeferredImportSelector#getImportGroup()；
	 * >>> 也就是从这里可以得知，如果是Group为null的情况下，必然会执行️DeferredImportSelector#selectImports()；如果Group不为null，就根据自定义的Group#process()逻辑而言，决定是否执行DeferredImportSelector#selectImports()
	 * （4）DeferredImportSelector.Group#selectImports()；
	 *
	 * 2、我们假设，如果返回的group为null，那么对于我们自定义的代码而言，看到的执行顺序是：
	 * （1）DeferredImportSelector#getImportGroup()；
	 * （2）DeferredImportSelector#getExclusionFilter()
	 * （3）DeferredImportSelector#selectImports()
	 *
	 * 3、我们假设，如果返回的group不为null，并且我们的Group#process()内部不执行我们的DeferredImportSelector#selectImports()，那么看到的执行顺序是：
	 * （1）DeferredImportSelector#getImportGroup()；
	 * （2）DeferredImportSelector#getExclusionFilter()
	 * （3）DeferredImportSelector.Group#process()；
	 * （4）DeferredImportSelector.Group#selectImports()
	 * 因为我们的的Group#process()内部不执行我们的DeferredImportSelector#selectImports()，所以看不到会执行我们自定义的DeferredImportSelector#selectImports()
	 */
	public static void test1() {
		ApplicationContext ac = new AnnotationConfigApplicationContext(MyDeferredImportSelectorHolder.class);
		HelloWorld bean = ac.getBean(HelloWorld.class);
		System.out.println(bean);
	}

	/**
	 * 场景：AConfig当中标注@Import(B.class)；B上面标注@Import(C.class)；C implements DeferredImportSelector
	 *
	 * 测试1：C还会被加载吗？
	 *
	 * 	会加载，首先AConfig上面标注@Import，所以A当作配置类被处理，于是就会解析处理@Import(B.class)中的B；
	 * 	又因为@Import(B.class)中的B，没有实现 会被当中配置类进行解析；于是就会识别到B上面的@Import(C.class)，进行处理C
	 *
	 * 测试2：在解析处理@Import(B.class)时，ConfigurationClass是指谁？在解析处理@Import(C.class)时，ConfigurationClass是指谁？
	 *
	 * 	在解析处理@Import(B.class)时，ConfigurationClass是AConfig，所以ConfigurationClass是直接标注了@Import的类
	 * 	在解析处理@Import(C.class)时，ConfigurationClass是B，所以ConfigurationClass是直接标注了@Import的类
	 * 	总结：在哪个类上标注的@Import，那么在处理@Import时，ConfigurationClass就是谁！
	 *
	 */
	public static void test2(){
		ApplicationContext ac = new AnnotationConfigApplicationContext(AConfig.class);
		HelloWorld bean = ac.getBean(HelloWorld.class);
		System.out.println(bean);
	}


	@Test
	public void whenFilterListWithCombinedPredicatesUsingOr_thenSuccess(){
		List<String> names = Arrays.asList("Adam", "Alexander", "John", "Tom");

		Predicate<String> predicate1 = str -> str.startsWith("J");
		Predicate<String> predicate2 =  str -> str.length() < 4;

		// 组合两个Predicate的逻辑，变为一个新的Predicate
		Predicate<String> or = predicate1.or(predicate2);

		List<String> result = names.stream()
				.filter(or)
				.collect(Collectors.toList());
	}

}