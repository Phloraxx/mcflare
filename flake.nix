{
  description = "Flake for Modflared";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {inherit system;config.allowUnfree = true;};
      in {
        devShell = pkgs.mkShell {
          name = "jvm-shell";

          buildInputs = with pkgs; [
            # Java
            gradle
            temurin-bin

            # IDEs
            jetbrains.idea-ultimate
          ];

          shellHook = ''
            export JAVA_HOME=${pkgs.temurin-bin}
          '';
        };
      }
    );
}
