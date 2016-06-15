from setuptools import setup, find_packages
from codecs import open
from os import path

here = path.abspath(path.dirname(__file__))

# Get the long description from the README file
with open(path.join(here, 'README.rst'), encoding='utf-8') as f:
    long_description = f.read()


setup(
        name='hazelcast-remote-controller',
        version='0.1',
        description='Hazelcast Remote Controller Python Library',
        long_description=long_description,
        url='https://github.com/hazelcast/hazelcast-remote-controller',
        author='Hazelcast Inc. Developers',
        author_email='hazelcast@googlegroups.com',
        classifiers=[
            #   3 - Alpha
            #   4 - Beta
            #   5 - Production/Stable
            'Development Status :: 3 - Alpha',
            'Intended Audience :: Developers',
            'Natural Language :: English',
            'Operating System :: OS Independent',
            'Programming Language :: Python',
            'Programming Language :: Python :: 2.7',
            'Topic :: Software Development :: Libraries :: Python Modules'
        ],
        license='Apache 2.0',
        keywords='hazelcast,client,python,test',
        packages=['hzrc'],
        install_requires=['thrift'],
)
