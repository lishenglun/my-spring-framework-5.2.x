package com.springstudy.Initializethebean.primary;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/31 4:57 下午
 */
@Primary
@Component
public class OperaSinger implements Singer {

	@Override
	public String sing(String lyrics) {
		return "I am singing in Bocelli voice: "+lyrics;
	}
}