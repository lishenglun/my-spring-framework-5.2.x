package com.springstudy.aop.useImportAware;

import com.springstudy.anno.ImportAwareAnnotation;
import com.springstudy.anno.Select;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 实现ImportAware
 */
// 若不需要获取此对象，则可以不加这个注解，也会因「@ImportAwareAnnotation」中「@Import(ImportAwareFreedom.class)」而执行setImportMetadata()。
// 也可以设置为配置类，如果作为配置类，就不能自己导入自己。
//@Component
public class ImportAwareFreedom implements ImportAware {

	public String importAwareAnnotationValue = "value";

	/**
	 * 设置导入ImportAwareFreedom类的@Configuration类上的所有注释元数据：
	 * 例如：A作为@Configuration类，其中导入了@Import(ImportAwareFreedom.class)，或者通过@ImportAwareAnnotation间接的导入了ImportAwareFreedom，那么importMetadata则为A类上的所有注解元数据
	 *
	 * @param importMetadata
	 */
	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		/* 获取所有注解 */
		Set<String> annotationTypes = importMetadata.getAnnotationTypes();
		System.out.println(annotationTypes);
		System.out.println("===============");
		/* 获取指定注解的信息 */
		Map<String, Object> annotationAttributes = importMetadata.getAnnotationAttributes(ImportAwareAnnotation.class.getName());
		if (annotationAttributes != null) {
			annotationAttributes.forEach((x, y) -> System.out.println(x + "====" + y));
			importAwareAnnotationValue = (String) annotationAttributes.get(importAwareAnnotationValue);
		}
	}

}