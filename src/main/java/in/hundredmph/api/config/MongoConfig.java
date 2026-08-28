package in.hundredmph.api.config;

import in.hundredmph.api.domain.billing.SubscriptionStatus;
import in.hundredmph.api.domain.user.UserRole;
import in.hundredmph.api.domain.user.UserStatus;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

/**
 * How documents are shaped on disk.
 *
 * <p>Two corrections to the defaults, both of which bit in the first smoke test:
 *
 * <ul>
 *   <li><b>Dates.</b> Spring Data stores a {@code LocalDate} as a BSON date at
 *       midnight in the JVM's zone, so {@code 2026-08-14} written by a server in
 *       Kolkata reads back as {@code 2026-08-13} on a server in UTC. A calendar
 *       day is not an instant (README §2.5), so it is stored as the string it
 *       is — which is also what makes the documents readable in the shell.
 *   <li><b>Enums.</b> Stored lower-cased to match the wire format and the app's
 *       own vocabulary, so a query written against the database uses the same
 *       spelling as a query written against the API.
 * </ul>
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                LocalDateToStringConverter.INSTANCE,
                StringToLocalDateConverter.INSTANCE,
                UserRoleToStringConverter.INSTANCE,
                StringToUserRoleConverter.INSTANCE,
                UserStatusToStringConverter.INSTANCE,
                StringToUserStatusConverter.INSTANCE,
                SubscriptionStatusToStringConverter.INSTANCE,
                StringToSubscriptionStatusConverter.INSTANCE));
    }

    /**
     * Drops the {@code _class} discriminator. Nothing here is persisted
     * polymorphically, so it is only noise in every document.
     *
     * <p>Static, because a BeanPostProcessor has to be created before the
     * configuration class it lives on is eligible for post-processing itself.
     */
    @Bean
    public static BeanPostProcessor removeMongoTypeHints() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof MappingMongoConverter converter) {
                    converter.setTypeMapper(new DefaultMongoTypeMapper(null));
                }
                return bean;
            }
        };
    }

    // ------------------------------------------------------------------ dates

    @WritingConverter
    enum LocalDateToStringConverter implements Converter<LocalDate, String> {
        INSTANCE;

        @Override
        public String convert(LocalDate source) {
            return source.format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    @ReadingConverter
    enum StringToLocalDateConverter implements Converter<String, LocalDate> {
        INSTANCE;

        @Override
        public LocalDate convert(String source) {
            return LocalDate.parse(source, DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    // ------------------------------------------------------------------ enums

    @WritingConverter
    enum UserRoleToStringConverter implements Converter<UserRole, String> {
        INSTANCE;

        @Override
        public String convert(UserRole source) {
            return source.wire();
        }
    }

    @ReadingConverter
    enum StringToUserRoleConverter implements Converter<String, UserRole> {
        INSTANCE;

        @Override
        public UserRole convert(String source) {
            return UserRole.from(source);
        }
    }

    @WritingConverter
    enum UserStatusToStringConverter implements Converter<UserStatus, String> {
        INSTANCE;

        @Override
        public String convert(UserStatus source) {
            return source.wire();
        }
    }

    @ReadingConverter
    enum StringToUserStatusConverter implements Converter<String, UserStatus> {
        INSTANCE;

        @Override
        public UserStatus convert(String source) {
            return UserStatus.from(source);
        }
    }

    @WritingConverter
    enum SubscriptionStatusToStringConverter implements Converter<SubscriptionStatus, String> {
        INSTANCE;

        @Override
        public String convert(SubscriptionStatus source) {
            return source.wire();
        }
    }

    @ReadingConverter
    enum StringToSubscriptionStatusConverter implements Converter<String, SubscriptionStatus> {
        INSTANCE;

        @Override
        public SubscriptionStatus convert(String source) {
            return SubscriptionStatus.from(source);
        }
    }
}
