package com.springstudy.Initializethebean.primary;

import org.springframework.context.annotation.Primary;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/31 4:56 下午
 */
@Primary
public interface Singer {

	String sing(String lyrics);

}