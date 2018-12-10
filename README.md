# Kotowari Restful

Kotowari-Restful is a RESTful API specific framework.

## Resource

A resource class should implements the Resource interface. 
 
```java
public interface Resource {
    Function<RestContext, ?> getFunction(DecisionPoint point);
}
```

### Class resource

A class resource is a useful for defining resource functions in a single class file as following.

```java
public class AddressesResource {
    @Inject
    private BeansValidator validator;

    @Inject
    private BeansConverter beansConverter;

    @Decision(value = MALFORMED, method={"GET"})
    public Problem isMalformed(Parameters params, RestContext context) {
        AddressSearchParams searchParams = beansConverter.createFrom(params, AddressSearchParams.class);
        context.putValue(searchParams);
        Set<ConstraintViolation<AddressSearchParams>> violations = validator.validate(searchParams);
        return violations.isEmpty() ? null : Problem.fromViolations(violations);
    }

    @Decision(value = MALFORMED, method={"POST"})
    public Problem isPostMalformed(Address address) {
        Set<ConstraintViolation<Address>> violations = validator.validate(address);
        return violations.isEmpty() ? null : Problem.fromViolations(violations);
    }

    @Decision(HANDLE_OK)
    public List<Address> handleOk(AddressSearchParams params, EntityManager em) {
        CriteriaBuilder builder = em.getCriteriaBuilder();
        CriteriaQuery<Address> query = builder.createQuery(Address.class);
        Root<Address> root = query.from(Address.class);
        query.select(root);

        return em.createQuery(query)
                .setFirstResult(params.getOffset())
                .setMaxResults(params.getLimit())
                .getResultList();
    }

    @Decision(POST)
    public Address handleCreated(Address address, EntityManager em) {
        EntityTransactionManager tx = new EntityTransactionManager(em);
        tx.required(() -> em.persist(address));
        return address;
    }
}
```

A class resource has a parent resource. It's used to define default resource functions. 

## ResourceInvokerMiddleware

ResourceInvokerMiddleware is a middleware for dispatching a request to the resource matching by routing.
