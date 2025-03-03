# DoD PKI Root and Intermediate CA certificates

## Source

A ZIP archive dated 2022-03-22 was fetched from [https://dl.cyber.mil/pki-pke/zip/unclass-certificates_pkcs7_DoD.zip]https://dl.cyber.mil/pki-pke/zip/unclass-certificates_pkcs7_DoD.zip) on 2025-03-02.

## Format conversions

The file `Certificates_PKCS7_v5_14_DoD.pem.p7b` was extracted from the ZIP archive. This is a PKCS#7 archive not directly usable by Nginx (which requires PEM) or Keycloak (which requires PKCS#12).

To create archives in the PEM and PKCS#12 formats, the shell script [`p7b-convert.sh`](p7b-convert.sh) was executed.
