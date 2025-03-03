#!/bin/bash

file_in=$1
file_base="${file_in%.*}"

# First convert from DER to PEM format
# provide filename, ie: Certificates_PKCS7_v5_14_DoD.pem.p7b

openssl pkcs7 -print_certs -in $file_in -out DoD_Root_CAs.pem

# Then create PKCS12 file, prompting for password
# docker-compose specifies KC_SPI_TRUSTSTORE_FILE_PASSWORD=password
openssl pkcs12 -export -nokeys  -out DoD_Root_CAs.p12 -in  DoD_Root_CAs.pem

