# =============================================================================
# Contact Method API scenario
#
# Run with:
#   hurl --variable host=http://localhost:3000 contact_methods.hurl
#
# Scenario:
#   1. Create a customer with one primary (email) contact method
#   2. GET the customer to verify
#   3. Add a secondary (postal) contact method
#   4. Promote the secondary to primary
#   5. Remove the demoted contact method
#   6. Error cases: duplicate / remove primary / promote non-secondary
# =============================================================================


# -----------------------------------------------------------------------------
# 1. Create a customer
# -----------------------------------------------------------------------------
POST {{host}}/customers
Content-Type: application/json
Accept: application/json
{
  "name": {
    "firstName": "Taro",
    "lastName": "Yamada"
  },
  "primaryContactMethod": {
    "type": "email",
    "label": "home",
    "emailAddress": "taro@example.com"
  },
  "secondaryContactMethods": []
}

HTTP 201
[Captures]
customer_id: jsonpath "$.id"
primary_cm_id_initial: jsonpath "$.primaryContactMethod.emailAddress" # used for reference only
[Asserts]
jsonpath "$.name.firstName" == "Taro"
jsonpath "$.name.lastName" == "Yamada"
jsonpath "$.primaryContactMethod.type" not exists  # flat fields, no type wrapper
jsonpath "$.primaryContactMethod.emailAddress" == "taro@example.com"
jsonpath "$.secondaryContactMethods" count == 0


# -----------------------------------------------------------------------------
# 2. GET the customer
# -----------------------------------------------------------------------------
GET {{host}}/customers/{{customer_id}}
Accept: application/json

HTTP 200
[Asserts]
jsonpath "$.id" == {{customer_id}}
jsonpath "$.primaryContactMethod.emailAddress" == "taro@example.com"
jsonpath "$.secondaryContactMethods" count == 0


# -----------------------------------------------------------------------------
# 3. Add a secondary contact method (postal address)
# -----------------------------------------------------------------------------
POST {{host}}/customers/{{customer_id}}/contact-methods
Content-Type: application/json
Accept: application/json
{
  "type": "postalAddress",
  "label": "work",
  "address1": "1-1 Marunouchi",
  "city": "Chiyoda",
  "state": "Tokyo",
  "zipCode": "100-0005"
}

HTTP 201
[Captures]
secondary_label: jsonpath "$.secondaryContactMethods[0].label"
[Asserts]
jsonpath "$.primaryContactMethod.emailAddress" == "taro@example.com"
jsonpath "$.secondaryContactMethods" count == 1
jsonpath "$.secondaryContactMethods[0].label" == "work"
jsonpath "$.secondaryContactMethods[0].address1" == "1-1 Marunouchi"


# -----------------------------------------------------------------------------
# 3a. Error: duplicate contact method
# -----------------------------------------------------------------------------
POST {{host}}/customers/{{customer_id}}/contact-methods
Content-Type: application/json
Accept: application/json
{
  "type": "postalAddress",
  "label": "work",
  "address1": "1-1 Marunouchi",
  "city": "Chiyoda",
  "state": "Tokyo",
  "zipCode": "100-0005"
}

HTTP 400
[Asserts]
jsonpath "$.violations[0].code" == "duplicate"


# -----------------------------------------------------------------------------
# 4. Promote the secondary to primary
#    — need the DB id of the secondary contact method; fetch it via GET
# -----------------------------------------------------------------------------
GET {{host}}/customers/{{customer_id}}
Accept: application/json

HTTP 200
[Captures]
secondary_cm_id: jsonpath "$.secondaryContactMethods[0].id"


PUT {{host}}/customers/{{customer_id}}/contact-methods/{{secondary_cm_id}}/primary
Accept: application/json

HTTP 200
[Asserts]
jsonpath "$.primaryContactMethod.label" == "work"
jsonpath "$.primaryContactMethod.address1" == "1-1 Marunouchi"
jsonpath "$.secondaryContactMethods[0].emailAddress" == "taro@example.com"


# -----------------------------------------------------------------------------
# 4a. Error: promote a contact method that is already primary
# -----------------------------------------------------------------------------
PUT {{host}}/customers/{{customer_id}}/contact-methods/{{secondary_cm_id}}/primary
Accept: application/json

HTTP 400
[Asserts]
jsonpath "$.violations[0].code" == "not_secondary"


# -----------------------------------------------------------------------------
# 5. Remove the demoted (now secondary) email contact method
#    — fetch current state to get the email CM's id
# -----------------------------------------------------------------------------
GET {{host}}/customers/{{customer_id}}
Accept: application/json

HTTP 200
[Captures]
email_cm_id: jsonpath "$.secondaryContactMethods[0].id"


DELETE {{host}}/customers/{{customer_id}}/contact-methods/{{email_cm_id}}
Accept: application/json

HTTP 204


# Verify the secondary list is now empty
GET {{host}}/customers/{{customer_id}}
Accept: application/json

HTTP 200
[Asserts]
jsonpath "$.secondaryContactMethods" count == 0
jsonpath "$.primaryContactMethod.label" == "work"


# -----------------------------------------------------------------------------
# 5a. Error: remove the primary contact method directly
# -----------------------------------------------------------------------------
GET {{host}}/customers/{{customer_id}}
Accept: application/json

HTTP 200
[Captures]
current_primary_id: jsonpath "$.primaryContactMethod.id"


DELETE {{host}}/customers/{{customer_id}}/contact-methods/{{current_primary_id}}
Accept: application/json

HTTP 400
[Asserts]
jsonpath "$.violations[0].code" == "cannot_remove_primary"


# -----------------------------------------------------------------------------
# 6. 404 cases
# -----------------------------------------------------------------------------
GET {{host}}/customers/99999
Accept: application/json

HTTP 404


POST {{host}}/customers/99999/contact-methods
Content-Type: application/json
Accept: application/json
{
  "type": "email",
  "label": "ghost",
  "emailAddress": "ghost@example.com"
}

HTTP 404


PUT {{host}}/customers/{{customer_id}}/contact-methods/99999/primary
Accept: application/json

HTTP 404


DELETE {{host}}/customers/{{customer_id}}/contact-methods/99999
Accept: application/json

HTTP 404
