package com.springstudy.importspring.ImportSelector.UserImportSelectOne;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 使用一：
 */
@Import(A.class)
@Configuration
public class UserImportSelectOne implements ImportSelector {

	@Override
	public String[] selectImports(AnnotationMetadata importingClassMetadata) {
		return new String[]{UserOne.class.getName()};
	}

}