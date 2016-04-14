# JarJarBinks
Advanced Java manipulation and analysis utility.

Currently includes three main features.

1. An ONF Transformer.
  ONF Transformers are much like access transformers as popularised by Minecraft Forge, however this one uses a custom format called .onf that combines access transformations with obfuscation values.
  BlazeLoader natively includes an ONF transformer and this utility serves to apply it's transformations in development environments.
  
2. Convert MCP (.srg) mappings into the mapping format used by Cuchas' Enigma decompiler/deobfuscator.

3. Class name mapping.
  JarJar uses advanced tree tecniques to compare and produce a best match between two different versions of the minecraft .jar.
  The result is a tree data structure mirroring the class hierarchy with each class from the first jar matched to an equivalent in the second jar.
  
  Once the tree is loaded JarJarBinks provides a command line with commands to query and modify this tree, as well a output it to a file in either raw text (save-tree), mcp (.srg) mappings, or as JSON which may be reloaded at a later time.
  
  
# Usage

Arguments in "[]" are required, and arguments in "{}" are optional.

For the access transformer:

    jarjar [ont] [jar] [state] {displace}
       onf - path to .onf file to apply
       jar - the jar file to transform
       state - obfuscation state (MCP|SRG|OBF)
       replace - Can be anything. If present the original file will be moved to a backup location and
                 replaced with the new one. Default behavious puts the output file alongside the input file
                 with a ".tmp" extension appended.

To convert mappings:

    jarjar enigma [srg] [minecraft-version.jar] [output]
       srg - path to notch-mcp.srg file. (It has to be this one, otherwise things will act weird.)
       jar - the minecraft.jar that the mappings apply to
       output - location where you want to save to resulting .enigma file

To do class matchings:

    jarjar remap [srg] [source] [path to destination jar] {json}
       srg - path to notch-mcp.srg file. (It has to be this one, otherwise things will act weird.)
       source - the jar that the mappings apply to. Classes will be mapped from this jar onto the destination
       destination - destination jar for the mappings to be mapped onto. Usually a newer version than source
       json - path to a json file containing a previously computed class tree. If this argument is present
              the tree from this fill will be loaded instead of computing a new one from the input jars
