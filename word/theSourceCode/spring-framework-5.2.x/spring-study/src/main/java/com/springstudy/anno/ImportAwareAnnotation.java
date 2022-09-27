package com.springstudy.anno;

import com.springstudy.aop.useImportAware.ImportAwareFreedom;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2020/8/29 11:17 下午
 * @Copyright topology technology group co.LTD. All rights reserved.
 * 注意：本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ImportAwareFreedom.class)
public @interface ImportAwareAnnotation {

	String value() default "testString";

}