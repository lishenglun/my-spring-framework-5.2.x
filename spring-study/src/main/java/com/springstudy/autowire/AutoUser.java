package com.springstudy.autowire;

import org.springframework.stereotype.Service;

/**
 * setAutoOrder2无法注入，因为名称为autoOrder2，bean容器中不存在此名称的bean，只有autoOrder名称的bean，所以无法注入！
 * 而setAutoMember可以注入！因为名称为autoMember，bean容器中存在此名称的bean，所以可以注入
 */
@Service
public class AutoUser {

	public AutoOrder autoOrder;

	public AutoMember autoMember;

	public void setAutoOrder2(AutoOrder autoOrder) {
		this.autoOrder = autoOrder;
	}

	public void setAutoMember(AutoMember autoMember) {
		this.autoMember = autoMember;
	}

	protected AutoUser(AutoOrder autoOrder) {
	}
}