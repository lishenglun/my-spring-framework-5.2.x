package com.springstudy.Initializethebean.autowireMode;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/31 5:59 下午
 */
public class AutowireModeA {

	// 自动装配
	private AutowireModeB autowireModeB;

	public AutowireModeB getAutowireModeB() {
		return autowireModeB;
	}

	public void setAutowireModeB(AutowireModeB autowireModeB) {
		this.autowireModeB = autowireModeB;
	}
}