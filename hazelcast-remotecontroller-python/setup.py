from setuptools import setup

# from codecs import open
from os import path

here = path.abspath(path.dirname(__file__))

setup(
        name='hazelcast-remote-controller',
        version='0.1',
        description='Hazelcast Remote Controller Python Library',
        license = 'Apache License 2.0',
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
        keywords='hazelcast,client,python,test',
        # packages=find_packages(exclude=['examples', 'docs', 'tests']),
        packages=['hazelcast', 'hazelcast.remotecontroller', ],
        # package_dir={'hazelcast': 'src'},
        install_requires=['thrift'],
)
