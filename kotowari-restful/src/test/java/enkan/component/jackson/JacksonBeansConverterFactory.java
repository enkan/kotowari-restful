package enkan.component.jackson;

public class JacksonBeansConverterFactory {
    public static JacksonBeansConverter create() {
        JacksonBeansConverter converter = new JacksonBeansConverter();
        converter.lifecycle().start(converter);
        return converter;
    }
}
