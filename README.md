# SonataBlendingTool

A project to support a consistent code-base generation from MEF API definitions and product specifications

# Project build

Project build with Maven

```shell script
mvn clean package
```

# Project run (CLI)

Project requires Java runtime in version 11 or greater.

```shell script
java -jar blender-1.x.jar <commang> [args]
```

## Tool `generate` command synopsis

```shell script
NAME
        sonata-blending-tool-cli generate - Generate code using configuration.

SYNOPSIS
        sonata-blending-tool-cli generate
                [ {-b | --blending-schema} <specifications to be blend (integrate) in>... ]
                [ {-c | --config} <configuration file> ]
                [ {-d | --spec-root-dir} <root directory for specificatins to be blended> ]
                [ {-e | -encoding} <files encoding> ]
                [ {-i | --input-spec} <spec file> ]
                [ {-m | --model-name} <model to be augmented> ]
                [ {-p | --product-spec} <product specifications>... ]
                [ --strict-mode ]

OPTIONS
        -b <specifications to be blend (integrate) in>, --blending-schema
        <specifications to be blend (integrate) in>
            sets of specifications (e.g. specific product or service
            definitions) you would like to integrate

            This option is part of the group 'allOrSelective' from which only
            one option may be specified


        -c <configuration file>, --config <configuration file>
            Path to configuration file configuration file. It can be json or
            yaml.If file is json, the content should have the format
            {"optionKey":"optionValue", "optionKey1":"optionValue1"...}.If file
            is yaml, the content should have the format optionKey:
            optionValueSupported options can be different for each language.
            Run config-help -g {generator name} command for language specific
            config options.

            This option may occur a maximum of 1 times


        -d <root directory for specificatins to be blended>, --spec-root-dir
        <root directory for specificatins to be blended>
            sets of product specification root directory for specifications you
            would like to integrate

        -e <files encoding>, -encoding <files encoding>
            encoding used to read API and product definitions. By default
            system encoding is used

        -i <spec file>, --input-spec <spec file>
            location of the OpenAPI spec, as URL or file (required)

            This option may occur a maximum of 1 times


        -m <model to be augmented>, --model-name <model to be augmented>
            Model which will be hosting product specific extensions (e.g.
            MEFProductConfiguration)

        -p <product specifications>, --product-spec <product specifications>
            sets of product specification you would like to integrate

            This option is part of the group 'allOrSelective' from which only
            one option may be specified


        --strict-mode
            Verify that model to be augmented allows for extension (contains
            discriminator definition).
            If strict-mode is `false` tool will add a discriminator on the fly
            if possible.
```

### Usage example

Assuming you have a valid spring generator configuration
(as explained [here](https://openapi-generator.tech/docs/generators/spring)) in `configurations/spring`

```shell script
java -jar .\blender-1.6-SNAPSHOT.jar generate -i .\productApi\serviceability\offeringQualification\productOfferingQualificationManagement.api.yaml \
     -c ./configurations/spring/spring-server.yaml \
     -d .\productSchema\carrierEthernet \ 
     -b accessEline\inventory\accessElineOvc.yaml -b carrierEthernetOperatorUni\inventory\carrierEthernetOperatorUni.yaml
```

## Tool `blend` command synopsis

Blend command generates an OAS 3 definition of combined API and product specifications

```shell script
NAME
        sonata-blending-tool-cli blend - Blend Product Specifications into
        OpenAPI.

SYNOPSIS
        sonata-blending-tool-cli blend
                [ {-b | --blending-schema} <specifications to be blend (integrate) in>... ]
                [ {-d | --spec-root-dir} <root directory for specificatins to be blended> ]
                [ {-e | -encoding} <files encoding> ]
                [ {-i | --input-spec} <spec file> ]
                [ {-m | --model-name} <model to be augmented> ]
                [ {-p | --product-spec} <product specifications>... ]
                [ --sorted ] [ --strict-mode ]

OPTIONS
        -b <specifications to be blend (integrate) in>, --blending-schema
        <specifications to be blend (integrate) in>
            sets of specifications (e.g. specific product or service
            definitions) you would like to integrate

            This option is part of the group 'allOrSelective' from which only
            one option may be specified


        -d <root directory for specificatins to be blended>, --spec-root-dir
        <root directory for specificatins to be blended>
            sets of product specification root directory for specifications you
            would like to integrate

        -e <files encoding>, -encoding <files encoding>
            encoding used to read API and product definitions. By default
            system encoding is used

        -i <spec file>, --input-spec <spec file>
            location of the OpenAPI spec, as URL or file (required)

            This option may occur a maximum of 1 times


        -m <model to be augmented>, --model-name <model to be augmented>
            Model which will be hosting product specific extensions (e.g.
            MEFProductConfiguration)

        -p <product specifications>, --product-spec <product specifications>
            sets of product specification you would like to integrate

            This option is part of the group 'allOrSelective' from which only
            one option may be specified


        --sorted
            sort data types in a lexical order

            This option may occur a maximum of 1 times


        --strict-mode
            Verify that model to be augmented allows for extension (contains
            discriminator definition).
            If strict-mode is `false` tool will add a discriminator on the fly
            if possible.        
```

### Usage example for Sonata

Assumption is that this command is run from root of the Sonata SDK directory and jar file is in the same directory.

```shell script
java -jar .\blender-1.6-SNAPSHOT.jar blend -i .\productApi\serviceability\offeringQualification\productOfferingQualificationManagement.api.yaml \
     -d .\productSchema\carrierEthernet \ 
     -b accessEline\inventory\accessElineOvc.yaml -b carrierEthernetOperatorUni\inventory\carrierEthernetOperatorUni.yaml
```

### Usage example for Legato

Assumption is that this command is run from root of the Legato SDK directory and jar file is in the same directory.

```shell script
 java -jar blender-1.6.jar blend -d spec/legato/carrierEthernet -m MefServiceConfiguration  \
     -i ./api/legato/serviceProvisioning/serviceOrdering/v4/serviceOrderingApi.openapi.yaml \ 
    -b carrierEthernetOvc.yaml -b carrierEthernetSubscriberUni.yaml
```