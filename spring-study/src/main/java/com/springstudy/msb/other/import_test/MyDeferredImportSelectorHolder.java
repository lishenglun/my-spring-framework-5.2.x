package com.springstudy.msb.other.import_test;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.AnnotationMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/9/27 21:46
 */
@Import(MyDeferredImportSelectorHolder.class)
public class MyDeferredImportSelectorHolder implements DeferredImportSelector {

	/**
	 * @param importingClassMetadata 标注@Import(A.class)注解的配置类的注解元数据，
	 *                               _________________________________例如：
	 *                               _________________________________@Import(A.class)
	 *                               _________________________________public class MyImportConfiguration{
	 *                               _________________________________}
	 *                               _________________________________MyImportConfiguration是标注@Import(A.class)注解的配置类
	 */
	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		System.out.println("MyDeferredImportSelectorHolder——————————>selectImports");
		return new String[]{HelloWorld.class.getName()};
	}

	@Override
	public Class<? extends Group> getImportGroup() {
		System.out.println("MyDeferredImportSelectorHolder——————————>getImportGroup");
		return DeferredImportSelector.super.getImportGroup();
	}

	@Override
	public Predicate<String> getExclusionFilter() {
		System.out.println("MyDeferredImportSelectorHolder——————————>getExclusionFilter");
		return new Predicate<String>() {
			@Override
			public boolean test(String className) {
				return "com.springstudy.msb.other.import_test.AConfig".equals(className);
			}
		};
	}

	public static class MyGroup implements Group {

		private AnnotationMetadata metadata;

		@Override
		public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
			System.out.println("MyGroup——————————>process()");

			this.metadata = metadata;
		}

		@Override
		public Iterable<Entry> selectImports() {
			System.out.println("MyGroup——————————>selectImports()");

			Entry entry = new Entry(metadata, HelloWorld.class.getName());

			List<Entry> list = new ArrayList<Entry>();
			list.add(entry);

			return list;
		}

	}


}