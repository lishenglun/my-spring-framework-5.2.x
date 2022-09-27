package com.springstudy.msb.s_07.selfAware;

import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description TODO
 * @date 2022/4/19 4:18 下午
 */
public class BeanAware implements EnvironmentAware {

	/**
	 * Set the {@code Environment} that this component runs in.
	 *
	 * @param environment
	 */
	@Override
	public void setEnvironment(Environment environment) {

	}

}