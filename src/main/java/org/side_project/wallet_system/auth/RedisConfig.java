package org.side_project.wallet_system.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
@SuppressWarnings("unchecked")
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 使用官方推薦的靜態工廠方法
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        // 設置 Key 與 Value 的序列化方式
        template.setKeySerializer(RedisSerializer.string()); // 等同於 StringRedisSerializer
        template.setValueSerializer(jsonSerializer);

        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
