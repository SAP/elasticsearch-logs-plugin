#!/bin/bash
#
# Creates a PKCS#12 truststore file from an PEM-encoded trusted
# certificate, or adds the certificate to the existing truststore
# file.
#
# The password of the truststore file is / must be '123456'.
#
# Importing the truststore in Jenkins:
#
# - Create a new credential of type Certificate
#   - Select the truststore file
#   - Enter the password '123456'
#
set -eu -o pipefail
exec >&2

readonly STORE_PASS=123456

function main() {
    if [[ $# != 2 ]]; then
        print_usage
        exit 1
    fi

    keytool \
        -importcert \
        -storetype PKCS12 \
        -keystore "$2" \
        -alias "trusted-$SRANDOM" \
        -file "$1" \
        -storepass "$STORE_PASS" \
        -noprompt

    echo ""
    echo "The truststore password is '$STORE_PASS'"

    echo ""
    echo "Truststore content:"
    echo ""
    keytool \
        -list \
        -keystore "$2" \
        -storepass "$STORE_PASS" \
        -noprompt \
        -v
}

function print_usage() {
    echo "Usage:"
    echo ""
    echo "  $(basename "$0") CERT_FILE TRUSTSTORE_FILE"
    echo ""
    echo "Arguments:"
    echo ""
    echo "  CERT_FILE"
    echo "    The PEM-encoded certificate to trust."
    echo ""
    echo "  TRUSTSTORE_FILE"
    echo "    The truststore file to create/extend."
    echo ""
}

main "$@"
