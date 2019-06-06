package kotowari.restful.example.entity;


import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.Serializable;

@Entity
public class Address implements Serializable {
    @Id
    private Long id;
    @Column(name = "CARE_OF")
    private String careOf;
    private String street;
    private String additional;
    private String city;
    private String zip;
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
