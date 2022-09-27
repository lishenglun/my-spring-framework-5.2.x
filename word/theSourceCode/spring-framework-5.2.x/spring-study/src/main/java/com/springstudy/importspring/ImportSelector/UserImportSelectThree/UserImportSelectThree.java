package com.springstudy.importspring.ImportSelector.UserImportSelectThree;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/23 8:14 下午
 */
public class UserImportSelectThree implements ImportSelector {

	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		/*
		 *
		 * ImportThreeBeanPostProcessor会把其注册为bean，然后ImportThreeBeanPostProcessor这个bean又实现了BeanPostProcessor，所以会走postProcessBeforeInitialization()方法，
		 * 在postProcessBeforeInitialization()方法中，Spring内部是循坏获取所有bean然后执行该方法，
		 * 所以在postProcessBeforeInitialization()方法内部逻辑中，当是UserThreeDaoImpl bean时就会对其进行代理
		 *
		 */
		return new String[]{ImportThreeBeanPostProcessor.class.getName()};
	}
}