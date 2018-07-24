package kotowari.restful.example.dao;

import kotowari.restful.example.entity.Address;
import org.seasar.doma.Dao;
import org.seasar.doma.Insert;
import org.seasar.doma.Select;
import org.seasar.doma.jdbc.SelectOptions;

import java.util.List;

@Dao
public interface AddressDao {
    @Select
    List<Address> findAll(SelectOptions options);

    @Insert
    int insert(Address address);
}
