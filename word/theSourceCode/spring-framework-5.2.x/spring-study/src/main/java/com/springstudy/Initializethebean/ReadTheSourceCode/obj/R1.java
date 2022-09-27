package com.springstudy.Initializethebean.ReadTheSourceCode.obj;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * TODO
 *
 * @author lishenglun
 * @version v1.1
 * @since 2020/9/2 9:24 下午
 */
@Service
public class R1 {

	@Autowired
	private R2 r2;

}