package com.springstudy.importspring.ImportSelector.ImportSelectorAdditionalTest;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Description cache mode select class
 * <p>
 *
 * @author wangmeng
 * <p>
 * @date 2019-10-15 15:53
 * <p>
 * Copyright topology technology group co.LTD. All rights reserved.
 * <p>
 * Notice 本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public abstract class PaasCachingModeImportSelector<A extends Annotation> implements ImportSelector {

	/**
	 * 选择启动类
	 *
	 * @param annotationMetadata 注解对象data
	 * @return 需要启动的类
	 */
	@Override
	public final String[] selectImports(AnnotationMetadata annotationMetadata) {
		Map<String, Object> annotationAttributes = annotationMetadata.getAnnotationAttributes(EnablePaasCache.class.getName());
		CacheMode cacheMode = (CacheMode) annotationAttributes.get("cacheMode");

		String[] imports = selectCacheMode(cacheMode);
		if (imports == null) {
			throw new IllegalArgumentException(String.format("Unknown cacheMode: '%s'", cacheMode));
		}
		return imports;
	}

	/**
	 * 加载不同的配置类
	 *
	 * @param cacheMode 缓存的模式
	 * @return 配置类名称
	 */
	protected abstract String[] selectCacheMode(CacheMode cacheMode);
}
