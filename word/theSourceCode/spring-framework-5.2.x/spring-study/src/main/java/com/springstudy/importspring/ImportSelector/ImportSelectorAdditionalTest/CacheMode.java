package com.springstudy.importspring.ImportSelector.ImportSelectorAdditionalTest;

/**
 * Description cache mode enum class
 * <p>
 *
 * @author wangmeng
 * <p>
 * @date 2019-10-15 15:58
 * <p>
 * Copyright topology technology group co.LTD. All rights reserved.
 * <p>
 * Notice 本内容仅限于授权后使用，禁止非授权传阅以及私自用于其他商业目的。
 */
public enum CacheMode {

    /**
     * 单机模式
     */
    SINGLE,

    /**
     * 哨兵模式
     */
    SENTINEL,

    /**
     * 集群模式
     */
    CLUSTER
}
