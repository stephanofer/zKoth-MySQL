# Project Instructions

## No negociables

- Quiero que el proyecto tenga la menor sobreingeniería posible. Queremos algo funcional,sin errores, que se mantenga simple en el sentido de complicar las cosas innesesariametne, que sesa escalable, fácil de mantener, auditable y depurable; es decir, no deberíamos introducir complejidad innecesaria que nos vaya a causar problemas más adelante. Dicho eso, esto no significa que vayamos a hacer las cosas mal; todo debe tener el mejor rendimiento y eficiencia, cada parte del codigo debe cumplir correctamente con sus responsabilidades, y queremos la menor cantidad posible de bugs y errores. Nada de problemas de rendimiento, nada de ineficiencias; queremos algo con una ultra-performance y sobre todo eficiente y no tener comportamietnos extraños.'
- Recuerda que el entorno de trabajo actual que esta trabajndo todo el equipo de trabajo es en powershell entonces todo lo que es terminal es powershell
- Bien tambien recuerda que no debes delegar a subagentes a menos que el usuario te lo indique si no te lo indica no delegues

## Runtime Validation

- Do not create smoke-test scripts, automated server harnesses, simulated servers, Docker test environments, or automatic server startup procedures unless the user explicitly requests them.
- Automated build checks and tests for isolated logic remain allowed, but they do not replace manual validation on the real server.
