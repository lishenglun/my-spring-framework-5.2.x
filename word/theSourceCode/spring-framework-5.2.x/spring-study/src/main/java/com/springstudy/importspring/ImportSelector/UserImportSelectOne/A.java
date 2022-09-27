package com.springstudy.importspring.ImportSelector.UserImportSelectOne;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/7/7 11:36 下午
 */
public class A implements DeferredImportSelector {

	/**
	 * Select and return the names of which class(es) should be imported based on
	 * the {@link AnnotationMetadata} of the importing @{@link Configuration} class.
	 * <p>
	 * 根据导入 @{@link Configuration} 类的 {@link AnnotationMetadata} 选择并返回应导入的类的名称。
	 *
	 * @param importingClassMetadata
	 * @return the class names, or an empty array if none —— 类名，如果没有则返回一个空数组
	 */
	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		return new String[]{UserOne.class.getName()};
	}

	//@Override
	//public Class<? extends Group> getImportGroup() {
	//	return AGroup.class;
	//}
}

class AGroup implements DeferredImportSelector.Group {

	/**
	 * Process the {@link AnnotationMetadata} of the importing @{@link Configuration}
	 * class using the specified {@link DeferredImportSelector}.
	 *
	 * @param metadata
	 * @param selector
	 */
	@Override
	public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {

	}

	/**
	 * Return the {@link Entry entries} of which class(es) should be imported
	 * for this group.
	 */
	@Override
	public Iterable<Entry> selectImports() {
		return null;
	}

}