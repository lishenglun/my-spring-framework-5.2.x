package com.springstudymvc.msb.other.test_annotation_ResponseStatus;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * @author lishenglun
 * @version v1.0.0
 * @description
 *
 *
 * 测试ServletInvocableHandlerMethod#invokeAndHandle()中的如下代码：
 *
 * 		else if (StringUtils.hasText(getResponseStatusReason())) {
 * 			mavContainer.setRequestHandled(true);
 * 			return;
 * 		}
 *
 * @date 2022/8/6 10:31 上午
 */
@Controller
public class TestAnnotationResponseStatusController {

	@RequestMapping("/responseStatus/hello")
	@ResponseStatus(value = HttpStatus.OK ,reason = "这里是原因哦")
	public String hello() {
		return "success";
	}

}