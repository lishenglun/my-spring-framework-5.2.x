package com.springstudy.importspring.ImportSelector.UserImportSelectTwo;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/23 8:08 下午
 */
public class UserImportSelectTwo implements ImportSelector {
	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		return new String[]{UserTwo.class.getName()};
	}
}