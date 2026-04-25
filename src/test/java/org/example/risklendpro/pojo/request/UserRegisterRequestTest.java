package org.example.risklendpro.pojo.request;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserRegisterRequestTest {
    
    @Test
    void testUserRegisterRequest() {
        UserRegisterRequest request = new UserRegisterRequest();
        request.setRealName("张三");
        request.setPhoneNumber("13800138000");
        request.setEmail("zhangsan@example.com");
        request.setIdCard("110101199001011234");
        request.setPassword("123456");
        request.setRepassword("123456");
        
        assertEquals("张三", request.getRealName());
        assertEquals("13800138000", request.getPhoneNumber());
        assertEquals("zhangsan@example.com", request.getEmail());
        assertEquals("110101199001011234", request.getIdCard());
        assertEquals("123456", request.getPassword());
        assertEquals("123456", request.getRepassword());
    }
}
