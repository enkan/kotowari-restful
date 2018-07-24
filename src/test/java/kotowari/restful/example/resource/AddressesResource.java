package kotowari.restful.example.resource;

import enkan.collection.Parameters;
import enkan.component.BeansConverter;
import enkan.component.doma2.DomaProvider;
import kotowari.restful.Decision;
import kotowari.restful.component.BeanValidator;
import kotowari.restful.data.Problem;
import kotowari.restful.data.RestContext;
import kotowari.restful.example.dao.AddressDao;
import kotowari.restful.example.entity.Address;
import org.seasar.doma.jdbc.SelectOptions;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import java.util.List;
import java.util.Set;

import static kotowari.restful.DecisionPoint.*;

public class AddressesResource {
    @Inject
    private BeanValidator validator;

    @Inject
    private DomaProvider daoProvider;

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
    public List<Address> handleOk(AddressSearchParams params) {
        AddressDao addressDao = daoProvider.getDao(AddressDao.class);
        SelectOptions options = SelectOptions.get();
        options.limit(params.getLimit());
        options.offset(params.getOffset());
        return addressDao.findAll(options);
    }

    @Decision(POST)
    public Address handleCreated(Address address) {
        AddressDao addressDao = daoProvider.getDao(AddressDao.class);
        addressDao.insert(address);
        return address;
    }
}
