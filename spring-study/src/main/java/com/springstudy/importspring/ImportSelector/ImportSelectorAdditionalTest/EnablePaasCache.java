package com.springstudy.importspring.ImportSelector.ImportSelectorAdditionalTest;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Description two level cache execution
 * <p>
 *
 * @author wangmeng
 * <p>
 * @date 2019-10-12 13:51
 * <p>
 * Copyright topology technology group co.LTD. All rights reserved.
 * <p>
 * Notice 本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({PaasCachingConfigurationSelector.class})
public @interface EnablePaasCache {

        /**
         * redis的模式(单机:single;哨兵:sentinel;切片:shard;集群:cluster;)
         * @return redis的缓存模式
         */
        CacheMode cacheMode() default CacheMode.SINGLE;
}
