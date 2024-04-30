from setuptools import setup

MAJOR = 1
MINOR = 0


PACKAGE_DIR = {
    "lvv_service_client": "target/lvv_service_client",  # noqa: E501
    "lvv_service_client.lvv_service_spec": "target/lvv_service_client/lvv_service_spec",  # noqa: E501
    "lvv_service_client.lvv_service_spec.models": "target/lvv_service_client/lvv_service_spec/models",  # noqa: E501
}


requires = [
    "oci",
]

setup(
    name="lvv-service-client",
    oci_version=(MAJOR, MINOR),
    write_version_module="target/lvv_service_client",
    description="LVV Service Python Client",
    author_email="opc_nwcp_dev_us_grp@oracle.com",
    packages=list(PACKAGE_DIR.keys()),  # swagger_client packaging.py expects list
    package_dir=PACKAGE_DIR,
    install_requires=requires,
    extras_require={
        "dev": [
            "mock",
            "pytest",
        ],
        "lint": [
            "black",
            "flake8",
            "isort",
        ],
        "yubi": [
            "nwauto-python-commons",  # nwcommons Not used in tests but useful for dev testing
            "yubi-utils",
        ],
    },
    zip_safe=False,
    swagger_model_packages={
        "lvv_service_client.lvv_service_spec.models": PACKAGE_DIR[
            "lvv_service_client.lvv_service_spec.models"
        ]
    },
    package_data={"lvv_service_client": ["py.typed", "api.yaml"]},
)
