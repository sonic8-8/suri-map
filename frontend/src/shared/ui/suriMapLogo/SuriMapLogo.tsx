import styles from './SuriMapLogo.module.css';
import suriMapLogoUrl from '../../../assets/Icon/SuriMap_Logo.png';

export type SuriMapLogoProps = {
  size?: number;
  className?: string;
  decorative?: boolean;
  variant?: 'compact' | 'brand';
};

const LOGO_SRC: Record<NonNullable<SuriMapLogoProps['variant']>, string> = {
  compact: suriMapLogoUrl,
  brand: '/surimap-favicon-white-outline-thin.svg',
};

export function SuriMapLogo({ size = 22, className, decorative = true, variant = 'compact' }: SuriMapLogoProps) {
  return (
    <img
      className={className ? `${styles.logo} ${className}` : styles.logo}
      src={LOGO_SRC[variant]}
      width={size}
      height={size}
      alt={decorative ? '' : 'Suri-Map logo'}
      aria-hidden={decorative}
      draggable={false}
    />
  );
}
