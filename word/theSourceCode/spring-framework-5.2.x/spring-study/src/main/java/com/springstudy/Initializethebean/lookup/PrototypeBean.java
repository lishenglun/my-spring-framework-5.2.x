package com.springstudy.Initializethebean.lookup;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/8/31 5:17 下午
 */
@Component
@Scope("prototype")
public class PrototypeBean {

	public void say() {
		System.out.println("say something...");
	}

}