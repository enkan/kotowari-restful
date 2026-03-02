package kotowari.restful.example.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

@Entity
public class Address implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "address_id_gen")
    @SequenceGenerator(name = "address_id_gen", sequenceName = "address_id_seq", allocationSize = 50)
    private Long id;
    @Column(name = "CARE_OF")
    private String careOf;
    @NotNull
    @Size(min = 1, max = 100)
    private String street;
    private String additional;
    @NotNull
    private String city;
    @NotNull
    @Size(min = 3, max = 20)
    private String zip;
    @NotNull
    @Size(min = 2, max = 2)
    @Column(name = "COUNTRY_CODE")
    private String countryCode;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCareOf() {
        return careOf;
    }

    public void setCareOf(String careOf) {
        this.careOf = careOf;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getAdditional() {
        return additional;
    }

    public void setAdditional(String additional) {
        this.additional = additional;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    @Override
    public String toString() {
        return "Address{" +
                "id=" + id +
                ", careOf='" + careOf + '\'' +
                ", street='" + street + '\'' +
                ", additional='" + additional + '\'' +
                ", city='" + city + '\'' +
                ", zip='" + zip + '\'' +
                ", countryCode='" + countryCode + '\'' +
                '}';
    }
}
