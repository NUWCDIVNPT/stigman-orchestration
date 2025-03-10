#!/bin/bash

file_in=$1

# Change pem.p7b to .pem format
# provide filename, ie: Certificates_PKCS7_v5_14_DoD.pem.p7b

openssl pkcs7 -print_certs -in $file_in -out DoD_Root_CAs.pem



