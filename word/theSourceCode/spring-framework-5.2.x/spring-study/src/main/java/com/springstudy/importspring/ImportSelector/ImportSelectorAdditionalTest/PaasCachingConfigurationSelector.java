package com.springstudy.importspring.ImportSelector.ImportSelectorAdditionalTest;

/**
 * Description configuration select class
 * <p>
 *
 * @author wangmeng
 * <p>
 * @date 2019-10-15 15:26
 * <p>
 * Copyright topology technology group co.LTD. All rights reserved.
 * <p>
 * Notice 本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public class PaasCachingConfigurationSelector extends PaasCachingModeImportSelector<EnablePaasCache> {

    private static final String EHCACHE_CACHE_CONFIGURATION_CLASS = "com.beingmate.cloud.paas.cache.config.EhcacheConfiguration";

    private static final String SINGLE_JEDIS_CACHE_CONFIGURATION_CLASS = "com.beingmate.cloud.paas.cache.config.JedisSingleConfiguration";

    private static final String SENTINEL_JEDIS_CACHE_CONFIGURATION_CLASS = "com.beingmate.cloud.paas.cache.config.JedisSentinelConfiguration";

    private static final String CLUSTER_JEDIS_CACHE_CONFIGURATION_CLASS = "com.beingmate.cloud.paas.cache.config.JedisClusterConfiguration";

    @Override
    protected String[] selectCacheMode(CacheMode cacheMode) {
        switch (cacheMode) {
            case SINGLE:
                return new String[]{SINGLE_JEDIS_CACHE_CONFIGURATION_CLASS/*, EHCACHE_CACHE_CONFIGURATION_CLASS*/};
            case SENTINEL:
                return new String[]{SENTINEL_JEDIS_CACHE_CONFIGURATION_CLASS/*, EHCACHE_CACHE_CONFIGURATION_CLASS */};
            case CLUSTER:
                return new String[]{CLUSTER_JEDIS_CACHE_CONFIGURATION_CLASS/*, EHCACHE_CACHE_CONFIGURATION_CLASS*/};
            default:
                return null;
        }
    }


}
