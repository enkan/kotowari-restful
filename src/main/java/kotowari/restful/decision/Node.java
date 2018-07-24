package kotowari.restful.decision;

import kotowari.restful.data.RestContext;

public interface Node<RESPONSE> {
    RESPONSE execute(RestContext context);
}
